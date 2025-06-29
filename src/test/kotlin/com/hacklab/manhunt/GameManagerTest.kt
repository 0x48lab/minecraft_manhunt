package com.hacklab.manhunt

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.*

/**
 * GameManagerの単体テスト
 * Mockitoを使用してBukkitのPlayer、JavaPluginをモック化
 */
class GameManagerTest {
    
    private lateinit var mockPlugin: Main
    private lateinit var mockConfigManager: ConfigManager
    private lateinit var gameManager: GameManager
    private lateinit var mockPlayer1: Player
    private lateinit var mockPlayer2: Player
    
    @BeforeEach
    fun setUp() {
        // モックオブジェクトの作成
        mockPlugin = mock(Main::class.java)
        mockConfigManager = mock(ConfigManager::class.java)
        mockPlayer1 = mock(Player::class.java)
        mockPlayer2 = mock(Player::class.java)
        
        // ConfigManagerのデフォルト値設定
        `when`(mockConfigManager.getMinPlayers()).thenReturn(2)
        `when`(mockConfigManager.getProximityLevel1()).thenReturn(1)
        `when`(mockConfigManager.getProximityLevel2()).thenReturn(2)
        `when`(mockConfigManager.getProximityLevel3()).thenReturn(3)
        `when`(mockConfigManager.getCompassUpdateInterval()).thenReturn(20L)
        `when`(mockConfigManager.getProximityCheckInterval()).thenReturn(20L)
        
        // プレイヤーモックの設定
        `when`(mockPlayer1.uniqueId).thenReturn(UUID.randomUUID())
        `when`(mockPlayer1.name).thenReturn("TestPlayer1")
        `when`(mockPlayer1.isOnline).thenReturn(true)
        `when`(mockPlayer1.isDead).thenReturn(false)
        
        `when`(mockPlayer2.uniqueId).thenReturn(UUID.randomUUID())
        `when`(mockPlayer2.name).thenReturn("TestPlayer2")
        `when`(mockPlayer2.isOnline).thenReturn(true)
        `when`(mockPlayer2.isDead).thenReturn(false)
        
        // GameManagerの初期化
        gameManager = GameManager(mockPlugin, mockConfigManager)
    }
    
    @Test
    fun `初期状態でゲーム状態がWAITINGであることを確認`() {
        assertEquals(GameState.WAITING, gameManager.getGameState())
    }
    
    @Test
    fun `プレイヤー追加と役割設定が正しく動作することを確認`() {
        // プレイヤーをハンターとして追加
        gameManager.addPlayer(mockPlayer1, PlayerRole.HUNTER)
        
        // 役割が正しく設定されていることを確認
        assertEquals(PlayerRole.HUNTER, gameManager.getPlayerRole(mockPlayer1))
        assertEquals(1, gameManager.getAllHunters().size)
        assertEquals(0, gameManager.getAllRunners().size)
    }
    
    @Test
    fun `役割変更が正しく動作することを確認`() {
        // プレイヤーを追加
        gameManager.addPlayer(mockPlayer1, PlayerRole.SPECTATOR)
        assertEquals(PlayerRole.SPECTATOR, gameManager.getPlayerRole(mockPlayer1))
        
        // 役割をランナーに変更
        gameManager.setPlayerRole(mockPlayer1, PlayerRole.RUNNER)
        assertEquals(PlayerRole.RUNNER, gameManager.getPlayerRole(mockPlayer1))
        assertEquals(1, gameManager.getAllRunners().size)
    }
    
    @Test
    fun `最小プレイヤー数の設定と取得が正しく動作することを確認`() {
        // デフォルト値の確認
        assertEquals(2, gameManager.getMinPlayers())
        
        // 値の変更
        gameManager.setMinPlayers(4)
        assertEquals(4, gameManager.getMinPlayers())
    }
    
    @Test
    fun `プレイヤー削除が正しく動作することを確認`() {
        // プレイヤーを追加
        gameManager.addPlayer(mockPlayer1, PlayerRole.HUNTER)
        assertEquals(1, gameManager.getAllHunters().size)
        
        // プレイヤーを削除
        gameManager.removePlayer(mockPlayer1, true)
        assertEquals(0, gameManager.getAllHunters().size)
        assertNull(gameManager.getPlayerRole(mockPlayer1))
    }
    
    @Test
    fun `ネットワーク切断処理が正しく動作することを確認`() {
        // ゲーム中の状態をシミュレート（プライベートフィールドにアクセスできないため、簡易テスト）
        gameManager.addPlayer(mockPlayer1, PlayerRole.RUNNER)
        
        // 切断として削除
        gameManager.removePlayer(mockPlayer1, false)
        
        // 再接続処理の確認
        val rejoined = gameManager.handleRejoin(mockPlayer1)
        // ゲームが進行中でないため、false が返されることを確認
        assertFalse(rejoined)
    }
    
    @Test
    fun `getAllメソッドが正しくフィルタリングすることを確認`() {
        // 複数のプレイヤーを異なる役割で追加
        gameManager.addPlayer(mockPlayer1, PlayerRole.HUNTER)
        gameManager.addPlayer(mockPlayer2, PlayerRole.RUNNER)
        
        // フィルタリング結果の確認
        assertEquals(1, gameManager.getAllHunters().size)
        assertEquals(1, gameManager.getAllRunners().size)
        assertEquals(0, gameManager.getAllSpectators().size)
        
        assertTrue(gameManager.getAllHunters().contains(mockPlayer1))
        assertTrue(gameManager.getAllRunners().contains(mockPlayer2))
    }
}