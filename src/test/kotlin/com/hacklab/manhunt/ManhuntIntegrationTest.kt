package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.configuration.file.FileConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.*
import java.util.logging.Logger

/**
 * Manhuntシステム全体の統合テスト
 * 実際のゲームフローをシミュレーション
 */
class ManhuntIntegrationTest {
    
    private lateinit var mockPlugin: Main
    private lateinit var mockConfig: FileConfiguration
    private lateinit var configManager: ConfigManager
    private lateinit var gameManager: GameManager
    private lateinit var compassTracker: CompassTracker
    private lateinit var manhuntCommand: ManhuntCommand
    
    private lateinit var hunter1: Player
    private lateinit var hunter2: Player
    private lateinit var runner1: Player
    private lateinit var runner2: Player
    private lateinit var spectator1: Player
    
    @BeforeEach
    fun setUp() {
        // プラグインとコンフィグのモック
        mockPlugin = mock(Main::class.java)
        mockConfig = mock(FileConfiguration::class.java)
        `when`(mockPlugin.config).thenReturn(mockConfig)
        `when`(mockPlugin.logger).thenReturn(mock(Logger::class.java))
        
        // デフォルト設定値の設定
        setupDefaultConfig()
        
        // システムコンポーネントの初期化
        configManager = ConfigManager(mockPlugin)
        gameManager = GameManager(mockPlugin, configManager)
        compassTracker = CompassTracker(mockPlugin, gameManager, configManager)
        manhuntCommand = ManhuntCommand(gameManager, compassTracker)
        
        // プレイヤーモックの作成
        hunter1 = createMockPlayer("Hunter1")
        hunter2 = createMockPlayer("Hunter2")
        runner1 = createMockPlayer("Runner1")
        runner2 = createMockPlayer("Runner2")
        spectator1 = createMockPlayer("Spectator1")
    }
    
    private fun setupDefaultConfig() {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(3)
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(1)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(3)
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(1.0)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(1.0)
        `when`(mockConfig.getString("messages.game-start")).thenReturn("§6[Manhunt] ゲーム開始！")
    }
    
    private fun createMockPlayer(name: String): Player {
        val player = mock(Player::class.java)
        `when`(player.uniqueId).thenReturn(UUID.randomUUID())
        `when`(player.name).thenReturn(name)
        `when`(player.isOnline).thenReturn(true)
        `when`(player.isDead).thenReturn(false)
        `when`(player.hasPermission("manhunt.admin")).thenReturn(false)
        `when`(player.world).thenReturn(mock(World::class.java))
        return player
    }
    
    @Test
    fun `完全なゲームフローのシミュレーション`() {
        // 初期状態の確認
        assertEquals(GameState.WAITING, gameManager.getGameState())
        assertEquals(3, gameManager.getMinPlayers())
        
        // Phase 1: プレイヤー参加
        gameManager.addPlayer(hunter1, PlayerRole.HUNTER)
        gameManager.addPlayer(runner1, PlayerRole.RUNNER)
        gameManager.addPlayer(spectator1, PlayerRole.SPECTATOR)
        
        // プレイヤー数チェック
        assertEquals(1, gameManager.getAllHunters().size)
        assertEquals(1, gameManager.getAllRunners().size)
        assertEquals(1, gameManager.getAllSpectators().size)
        
        assertTrue(gameManager.getAllHunters().contains(hunter1))
        assertTrue(gameManager.getAllRunners().contains(runner1))
        assertTrue(gameManager.getAllSpectators().contains(spectator1))
        
        // Phase 2: 役割変更
        gameManager.setPlayerRole(spectator1, PlayerRole.HUNTER)
        assertEquals(2, gameManager.getAllHunters().size)
        assertEquals(0, gameManager.getAllSpectators().size)
        assertEquals(PlayerRole.HUNTER, gameManager.getPlayerRole(spectator1))
        
        // Phase 3: 最小プレイヤー数設定
        gameManager.setMinPlayers(2)
        assertEquals(2, gameManager.getMinPlayers())
        
        // Phase 4: プレイヤー退出（意図的）
        gameManager.removePlayer(spectator1, true)
        assertEquals(1, gameManager.getAllHunters().size)
        assertNull(gameManager.getPlayerRole(spectator1))
        
        // Phase 5: プレイヤー切断シミュレーション
        gameManager.removePlayer(runner1, false) // 切断として処理
        assertEquals(0, gameManager.getAllRunners().size)
        
        // Phase 6: 再接続テスト（ゲームが進行中でないため失敗するはず）
        val rejoinResult = gameManager.handleRejoin(runner1)
        assertFalse(rejoinResult) // ゲームが進行中でないため再接続は失敗
    }
    
    @Test
    fun `コマンドシステムとゲームマネージャーの統合テスト`() {
        // コマンド経由でプレイヤー参加
        val mockCommand = mock(org.bukkit.command.Command::class.java)
        
        // join コマンド
        val joinResult = manhuntCommand.onCommand(hunter1, mockCommand, "manhunt", arrayOf("join", "hunter"))
        assertTrue(joinResult)
        assertEquals(PlayerRole.HUNTER, gameManager.getPlayerRole(hunter1))
        
        // role コマンド
        val roleResult = manhuntCommand.onCommand(hunter1, mockCommand, "manhunt", arrayOf("role", "runner"))
        assertTrue(roleResult)
        assertEquals(PlayerRole.RUNNER, gameManager.getPlayerRole(hunter1))
        
        // status コマンド
        val statusResult = manhuntCommand.onCommand(hunter1, mockCommand, "manhunt", arrayOf("status"))
        assertTrue(statusResult)
        
        // leave コマンド
        val leaveResult = manhuntCommand.onCommand(hunter1, mockCommand, "manhunt", arrayOf("leave"))
        assertTrue(leaveResult)
        assertNull(gameManager.getPlayerRole(hunter1))
    }
    
    @Test
    fun `管理者コマンドシステムの統合テスト`() {
        val mockAdmin = mock(Player::class.java)
        `when`(mockAdmin.hasPermission("manhunt.admin")).thenReturn(true)
        `when`(mockAdmin.uniqueId).thenReturn(UUID.randomUUID())
        `when`(mockAdmin.name).thenReturn("Admin")
        
        val mockCommand = mock(org.bukkit.command.Command::class.java)
        
        // プレイヤーを追加
        gameManager.addPlayer(runner1, PlayerRole.RUNNER)
        
        // sethunter コマンド（オンラインプレイヤーチェックはスキップ）
        mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Player> { Bukkit.getPlayer("Runner1") }.thenReturn(runner1)
            
            val setHunterResult = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", 
                arrayOf("sethunter", "Runner1"))
            assertTrue(setHunterResult)
            assertEquals(PlayerRole.HUNTER, gameManager.getPlayerRole(runner1))
        }
        
        // minplayers コマンド
        val minPlayersResult = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", 
            arrayOf("minplayers", "5"))
        assertTrue(minPlayersResult)
        assertEquals(5, gameManager.getMinPlayers())
        
        // start コマンド
        val startResult = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", arrayOf("start"))
        assertTrue(startResult)
        
        // reload コマンド
        val reloadResult = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", arrayOf("reload"))
        assertTrue(reloadResult)
    }
    
    @Test
    fun `設定管理とゲームシステムの統合テスト`() {
        // 設定値の変更
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(5)
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(2)
        
        // 新しいConfigManagerを作成
        val newConfigManager = ConfigManager(mockPlugin)
        val newGameManager = GameManager(mockPlugin, newConfigManager)
        
        // 設定値が反映されることを確認
        assertEquals(5, newConfigManager.getMinPlayers())
        assertEquals(2, newConfigManager.getProximityLevel1())
        
        // GameManagerに設定が反映されることを確認
        assertEquals(5, newGameManager.getMinPlayers())
    }
    
    @Test
    fun `設定値検証の統合テスト`() {
        // 無効な設定値を設定
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(1) // 無効（最小値未満）
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(5) // 無効（順序逆転）
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(3)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(1)
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(-1.0) // 無効
        
        // 検証と修正が実行されることを確認
        configManager.validateAndFixConfig()
        
        // 修正メソッドが呼ばれることを確認
        verify(mockConfig).set("game.min-players", 2)
        verify(mockConfig).set("game.proximity-warning.level-1", 1)
        verify(mockConfig).set("game.proximity-warning.level-2", 2)
        verify(mockConfig).set("game.proximity-warning.level-3", 3)
        verify(mockConfig).set("game.compass-update-interval", 1.0)
        verify(mockPlugin, atLeastOnce()).saveConfig()
    }
    
    @Test
    fun `複数プレイヤーでの複雑なシナリオテスト`() {
        // 5人のプレイヤーが参加
        gameManager.addPlayer(hunter1, PlayerRole.HUNTER)
        gameManager.addPlayer(hunter2, PlayerRole.HUNTER)
        gameManager.addPlayer(runner1, PlayerRole.RUNNER)
        gameManager.addPlayer(runner2, PlayerRole.RUNNER)
        gameManager.addPlayer(spectator1, PlayerRole.SPECTATOR)
        
        // 初期状態確認
        assertEquals(2, gameManager.getAllHunters().size)
        assertEquals(2, gameManager.getAllRunners().size)
        assertEquals(1, gameManager.getAllSpectators().size)
        
        // プレイヤーの死亡シミュレーション
        `when`(runner1.isDead).thenReturn(true)
        
        // 勝利条件チェック（生存者がいるため、まだゲーム続行）
        gameManager.checkWinConditions()
        assertEquals(GameState.WAITING, gameManager.getGameState()) // まだ開始していない
        
        // 全ランナーが死亡
        `when`(runner2.isDead).thenReturn(true)
        gameManager.checkWinConditions()
        // ゲームが開始されていないため、状態は変わらない
        assertEquals(GameState.WAITING, gameManager.getGameState())
        
        // プレイヤーの切断と再接続
        gameManager.removePlayer(hunter1, false) // 切断
        assertEquals(1, gameManager.getAllHunters().size)
        
        // 再接続を試行（ゲーム進行中でないため失敗）
        assertFalse(gameManager.handleRejoin(hunter1))
    }
    
    @Test
    fun `タブ補完システムの統合テスト`() {
        val mockCommand = mock(org.bukkit.command.Command::class.java)
        
        // 通常ユーザーのタブ補完
        val userCompletions = manhuntCommand.onTabComplete(hunter1, mockCommand, "manhunt", arrayOf(""))
        assertNotNull(userCompletions)
        assertTrue(userCompletions!!.contains("join"))
        assertTrue(userCompletions.contains("leave"))
        assertFalse(userCompletions.contains("start")) // 管理者専用
        
        // 管理者のタブ補完
        val mockAdmin = mock(Player::class.java)
        `when`(mockAdmin.hasPermission("manhunt.admin")).thenReturn(true)
        
        val adminCompletions = manhuntCommand.onTabComplete(mockAdmin, mockCommand, "manhunt", arrayOf(""))
        assertNotNull(adminCompletions)
        assertTrue(adminCompletions!!.contains("start"))
        assertTrue(adminCompletions.contains("sethunter"))
        assertTrue(adminCompletions.contains("reload"))
        
        // role コマンドの引数補完
        val roleCompletions = manhuntCommand.onTabComplete(hunter1, mockCommand, "manhunt", arrayOf("role", ""))
        assertNotNull(roleCompletions)
        assertTrue(roleCompletions!!.contains("runner"))
        assertTrue(roleCompletions.contains("hunter"))
        assertTrue(roleCompletions.contains("spectator"))
    }
}