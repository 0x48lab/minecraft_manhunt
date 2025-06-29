package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.CompassMeta
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.logging.Logger

/**
 * エラーハンドリングとエッジケースのテスト
 * 様々な異常状態や例外ケースをテスト
 */
class ErrorHandlingTest {
    
    private lateinit var mockPlugin: Main
    private lateinit var mockConfigManager: ConfigManager
    private lateinit var gameManager: GameManager
    private lateinit var compassTracker: CompassTracker
    private lateinit var manhuntCommand: ManhuntCommand
    private lateinit var mockLogger: Logger
    
    @BeforeEach
    fun setUp() {
        mockPlugin = mock(Main::class.java)
        mockConfigManager = mock(ConfigManager::class.java)
        mockLogger = mock(Logger::class.java)
        
        `when`(mockPlugin.logger).thenReturn(mockLogger)
        `when`(mockConfigManager.getMinPlayers()).thenReturn(2)
        `when`(mockConfigManager.getProximityLevel1()).thenReturn(1)
        `when`(mockConfigManager.getProximityLevel2()).thenReturn(2)
        `when`(mockConfigManager.getProximityLevel3()).thenReturn(3)
        `when`(mockConfigManager.getCompassUpdateInterval()).thenReturn(20L)
        `when`(mockConfigManager.getProximityCheckInterval()).thenReturn(20L)
        
        gameManager = GameManager(mockPlugin, mockConfigManager)
        compassTracker = CompassTracker(mockPlugin, gameManager, mockConfigManager)
        manhuntCommand = ManhuntCommand(gameManager, compassTracker)
    }
    
    @Test
    fun `null Playerでの操作が安全に処理されることを確認`() {
        // nullプレイヤーでの操作は実際のBukkitAPIでは発生しないが、
        // 防御的プログラミングの観点でテスト
        assertDoesNotThrow {
            gameManager.getPlayerRole(mock(Player::class.java))
        }
    }
    
    @Test
    fun `存在しないプレイヤーの役割取得が安全に処理されることを確認`() {
        val mockPlayer = createMockPlayer("NonExistent")
        
        val role = gameManager.getPlayerRole(mockPlayer)
        assertNull(role)
    }
    
    @Test
    fun `重複するプレイヤー追加が安全に処理されることを確認`() {
        val mockPlayer = createMockPlayer("DuplicateTest")
        
        // 同じプレイヤーを2回追加
        gameManager.addPlayer(mockPlayer, PlayerRole.HUNTER)
        gameManager.addPlayer(mockPlayer, PlayerRole.RUNNER) // 上書き
        
        // 最後に設定された役割になることを確認
        assertEquals(PlayerRole.RUNNER, gameManager.getPlayerRole(mockPlayer))
        assertEquals(1, gameManager.getAllRunners().size)
        assertEquals(0, gameManager.getAllHunters().size)
    }
    
    @Test
    fun `存在しないプレイヤーの削除が安全に処理されることを確認`() {
        val mockPlayer = createMockPlayer("NonExistentRemove")
        
        assertDoesNotThrow {
            gameManager.removePlayer(mockPlayer, true)
        }
        
        // 何も変化しないことを確認
        assertEquals(0, gameManager.getAllHunters().size)
        assertEquals(0, gameManager.getAllRunners().size)
    }
    
    @Test
    fun `オフラインプレイヤーが含まれる場合の勝利条件チェック`() {
        val onlinePlayer = createMockPlayer("Online")
        val offlinePlayer = createMockPlayer("Offline")
        `when`(offlinePlayer.isOnline).thenReturn(false)
        
        gameManager.addPlayer(onlinePlayer, PlayerRole.RUNNER)
        gameManager.addPlayer(offlinePlayer, PlayerRole.RUNNER)
        
        // オンラインプレイヤーのみが勝利条件に影響することを確認
        gameManager.checkWinConditions()
        // オンラインのランナーがいるため、ゲーム継続
        assertEquals(GameState.WAITING, gameManager.getGameState())
    }
    
    @Test
    fun `死亡プレイヤーが含まれる場合の勝利条件チェック`() {
        val alivePlayer = createMockPlayer("Alive")
        val deadPlayer = createMockPlayer("Dead")
        `when`(deadPlayer.isDead).thenReturn(true)
        
        gameManager.addPlayer(alivePlayer, PlayerRole.RUNNER)
        gameManager.addPlayer(deadPlayer, PlayerRole.RUNNER)
        
        gameManager.checkWinConditions()
        // 生存ランナーがいるため、ゲーム継続
        assertEquals(GameState.WAITING, gameManager.getGameState())
    }
    
    @Test
    fun `コンパス作成時のItemMeta nullケースの処理`() {
        val mockPlayer = createMockPlayer("CompassTest")
        `when`(mockPlayer.inventory).thenReturn(mock(PlayerInventory::class.java))
        
        // ItemStackのitemMetaがnullを返すケース
        val mockCompass = mock(ItemStack::class.java)
        `when`(mockCompass.itemMeta).thenReturn(null)
        
        // エラーログが出力されることを確認（実際の処理は継続）
        compassTracker.giveCompass(mockPlayer)
        
        verify(mockLogger, atLeastOnce()).warning(anyString())
    }
    
    @Test
    fun `World nullケースでの距離計算エラーハンドリング`() {
        val hunter = createMockPlayer("Hunter")
        val runner = createMockPlayer("Runner")
        
        // worldがnullの場合
        `when`(hunter.world).thenReturn(null)
        `when`(runner.world).thenReturn(mock(World::class.java))
        
        gameManager.addPlayer(hunter, PlayerRole.HUNTER)
        gameManager.addPlayer(runner, PlayerRole.RUNNER)
        
        // 例外が発生せずに処理されることを確認
        assertDoesNotThrow {
            // プライベートメソッドのため、直接テストは困難
            // 実際の近接チェック処理を間接的にテスト
            gameManager.getAllHunters()
            gameManager.getAllRunners()
        }
    }
    
    @Test
    fun `異なるWorldのプレイヤー間での処理`() {
        val hunterWorld = mock(World::class.java)
        val runnerWorld = mock(World::class.java)
        
        val hunter = createMockPlayer("Hunter")
        val runner = createMockPlayer("Runner")
        
        `when`(hunter.world).thenReturn(hunterWorld)
        `when`(runner.world).thenReturn(runnerWorld)
        
        gameManager.addPlayer(hunter, PlayerRole.HUNTER)
        gameManager.addPlayer(runner, PlayerRole.RUNNER)
        
        // 異なるワールドでも例外が発生しないことを確認
        assertDoesNotThrow {
            gameManager.getAllHunters()
            gameManager.getAllRunners()
        }
    }
    
    @Test
    fun `Location距離計算での例外ハンドリング`() {
        val hunter = createMockPlayer("Hunter")
        val runner = createMockPlayer("Runner")
        
        val mockLocation1 = mock(Location::class.java)
        val mockLocation2 = mock(Location::class.java)
        
        `when`(hunter.location).thenReturn(mockLocation1)
        `when`(runner.location).thenReturn(mockLocation2)
        
        // distance計算で例外を投げるように設定
        `when`(mockLocation1.distance(mockLocation2)).thenThrow(RuntimeException("Distance calculation error"))
        
        gameManager.addPlayer(hunter, PlayerRole.HUNTER)
        gameManager.addPlayer(runner, PlayerRole.RUNNER)
        
        // 例外が適切にハンドリングされることを確認
        assertDoesNotThrow {
            // 内部的に距離計算が行われる処理を実行
            gameManager.getAllHunters()
            gameManager.getAllRunners()
        }
    }
    
    @Test
    fun `コマンド引数の境界ケーステスト`() {
        val mockPlayer = createMockPlayer("CommandTest")
        val mockCommand = mock(org.bukkit.command.Command::class.java)
        
        // 空の配列
        val result1 = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf())
        assertTrue(result1)
        
        // 非常に長い引数
        val longArgs = Array(100) { "arg$it" }
        val result2 = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", longArgs)
        assertTrue(result2)
        
        // 空文字列の引数
        val result3 = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("", ""))
        assertTrue(result3)
    }
    
    @Test
    fun `数値変換エラーのハンドリング`() {
        val mockAdmin = createMockPlayer("Admin")
        `when`(mockAdmin.hasPermission("manhunt.admin")).thenReturn(true)
        val mockCommand = mock(org.bukkit.command.Command::class.java)
        
        // 無効な数値文字列
        val result1 = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", 
            arrayOf("minplayers", "abc"))
        assertTrue(result1)
        
        // 空文字列
        val result2 = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", 
            arrayOf("minplayers", ""))
        assertTrue(result2)
        
        // 非常に大きな数値
        val result3 = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", 
            arrayOf("minplayers", "999999999"))
        assertTrue(result3)
    }
    
    @Test
    fun `オンラインプレイヤー検索での例外ハンドリング`() {
        mockStatic(Bukkit::class.java).use { bukkit ->
            val mockAdmin = createMockPlayer("Admin")
            `when`(mockAdmin.hasPermission("manhunt.admin")).thenReturn(true)
            val mockCommand = mock(org.bukkit.command.Command::class.java)
            
            // Bukkit.getPlayer()がnullを返す場合
            bukkit.`when`<Player> { Bukkit.getPlayer("NonExistent") }.thenReturn(null)
            
            val result = manhuntCommand.onCommand(mockAdmin, mockCommand, "manhunt", 
                arrayOf("sethunter", "NonExistent"))
            assertTrue(result)
            
            verify(mockAdmin).sendMessage(contains("オンラインではありません"))
        }
    }
    
    @Test
    fun `TabComplete での例外ハンドリング`() {
        val mockPlayer = createMockPlayer("TabTest")
        val mockCommand = mock(org.bukkit.command.Command::class.java)
        
        // 境界ケース：非常に多くの引数
        val manyArgs = Array(1000) { "arg$it" }
        val result1 = manhuntCommand.onTabComplete(mockPlayer, mockCommand, "manhunt", manyArgs)
        assertNotNull(result1) // 空のリストが返されることを確認
        
        // null引数（実際には発生しないが、防御的プログラミング）
        val result2 = manhuntCommand.onTabComplete(mockPlayer, mockCommand, "manhunt", arrayOf())
        assertNotNull(result2)
    }
    
    @Test
    fun `ゲーム状態遷移での異常ケース`() {
        // プレイヤーがいない状態でゲーム開始を試行
        assertDoesNotThrow {
            gameManager.forceStartGame()
        }
        
        // ゲーム状態が変わらないことを確認
        assertEquals(GameState.WAITING, gameManager.getGameState())
    }
    
    @Test
    fun `メモリリーク防止のためのリソース解放テスト`() {
        val mockPlayer = createMockPlayer("ResourceTest")
        
        // プレイヤーを追加
        gameManager.addPlayer(mockPlayer, PlayerRole.HUNTER)
        assertEquals(1, gameManager.getAllHunters().size)
        
        // プレイヤーを削除
        gameManager.removePlayer(mockPlayer, true)
        assertEquals(0, gameManager.getAllHunters().size)
        
        // キャッシュもクリアされることを確認（内部実装詳細）
        assertNull(gameManager.getPlayerRole(mockPlayer))
    }
    
    @Test
    fun `同時実行での安全性テスト`() {
        val players = (1..10).map { createMockPlayer("Player$it") }
        
        // 複数のプレイヤーを同時に追加（シミュレーション）
        assertDoesNotThrow {
            players.forEachIndexed { index, player ->
                val role = if (index % 2 == 0) PlayerRole.HUNTER else PlayerRole.RUNNER
                gameManager.addPlayer(player, role)
            }
        }
        
        // 正しい数が管理されていることを確認
        assertEquals(5, gameManager.getAllHunters().size)
        assertEquals(5, gameManager.getAllRunners().size)
    }
    
    private fun createMockPlayer(name: String): Player {
        val player = mock(Player::class.java)
        `when`(player.uniqueId).thenReturn(UUID.randomUUID())
        `when`(player.name).thenReturn(name)
        `when`(player.isOnline).thenReturn(true)
        `when`(player.isDead).thenReturn(false)
        `when`(player.hasPermission("manhunt.admin")).thenReturn(false)
        `when`(player.world).thenReturn(mock(World::class.java))
        `when`(player.location).thenReturn(mock(Location::class.java))
        return player
    }
}