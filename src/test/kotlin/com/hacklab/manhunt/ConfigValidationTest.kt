package com.hacklab.manhunt

import org.bukkit.configuration.file.FileConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import java.util.logging.Logger

/**
 * 設定ファイル機能の詳細テスト
 * 様々なエッジケースと無効な設定値のテスト
 */
class ConfigValidationTest {
    
    private lateinit var mockPlugin: Main
    private lateinit var mockConfig: FileConfiguration
    private lateinit var mockLogger: Logger
    private lateinit var configManager: ConfigManager
    
    @BeforeEach
    fun setUp() {
        mockPlugin = mock(Main::class.java)
        mockConfig = mock(FileConfiguration::class.java)
        mockLogger = mock(Logger::class.java)
        
        `when`(mockPlugin.config).thenReturn(mockConfig)
        `when`(mockPlugin.logger).thenReturn(mockLogger)
        
        configManager = ConfigManager(mockPlugin)
    }
    
    @Test
    fun `デフォルト設定値が正しく返されることを確認`() {
        // 全ての設定でnullを返すように設定（デフォルト値テスト）
        `when`(mockConfig.getInt(anyString(), anyInt())).thenAnswer { it.arguments[1] }
        `when`(mockConfig.getDouble(anyString(), anyDouble())).thenAnswer { it.arguments[1] }
        `when`(mockConfig.getString(anyString())).thenReturn(null)
        
        // デフォルト値の確認
        assertEquals(2, configManager.getMinPlayers())
        assertEquals(1, configManager.getProximityLevel1())
        assertEquals(2, configManager.getProximityLevel2())
        assertEquals(3, configManager.getProximityLevel3())
        assertEquals(20L, configManager.getCompassUpdateInterval()) // 1.0 * 20
        assertEquals(20L, configManager.getProximityCheckInterval()) // 1.0 * 20
        
        assertEquals("§6[Manhunt] ゲーム開始！", configManager.getGameStartMessage())
        assertEquals("§c追う人の勝利！逃げる人を全員倒しました！", configManager.getHunterWinMessage())
        assertEquals("§a逃げる人の勝利！エンダードラゴンを倒しました！", configManager.getRunnerWinMessage())
        assertEquals("§6[Manhunt] 新しいゲームの準備ができました！", configManager.getGameResetMessage())
        
        assertEquals("§c§l[警告] 追う人が1チャンク以内にいます！", configManager.getProximityWarningLevel1())
        assertEquals("§6§l[警告] 追う人が2チャンク以内にいます！", configManager.getProximityWarningLevel2())
        assertEquals("§e§l[警告] 追う人が3チャンク以内にいます！", configManager.getProximityWarningLevel3())
    }
    
    @ParameterizedTest
    @ValueSource(ints = [-5, -1, 0, 1])
    fun `無効な最小プレイヤー数が修正されることを確認`(invalidValue: Int) {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(invalidValue)
        
        configManager.validateAndFixConfig()
        
        verify(mockConfig).set("game.min-players", 2)
        verify(mockPlugin).saveConfig()
        verify(mockLogger).warning("最小プレイヤー数が無効だったため、2に修正しました。")
    }
    
    @Test
    fun `有効な最小プレイヤー数は修正されないことを確認`() {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(5)
        setupValidProximityWarnings()
        setupValidUpdateIntervals()
        
        configManager.validateAndFixConfig()
        
        verify(mockConfig, never()).set("game.min-players", anyInt())
        verify(mockPlugin, never()).saveConfig()
    }
    
    @ParameterizedTest
    @CsvSource(
        "0, 2, 3",  // level1が無効
        "1, 0, 3",  // level2が無効
        "1, 2, 0",  // level3が無効
        "-1, -1, -1", // 全て無効
        "3, 2, 1",  // 順序が逆
        "2, 2, 2",  // 全て同じ
        "1, 3, 2"   // level2とlevel3の順序が逆
    )
    fun `無効な近接警告レベルが修正されることを確認`(level1: Int, level2: Int, level3: Int) {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(level1)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(level2)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(level3)
        setupValidUpdateIntervals()
        
        configManager.validateAndFixConfig()
        
        verify(mockConfig).set("game.proximity-warning.level-1", 1)
        verify(mockConfig).set("game.proximity-warning.level-2", 2)
        verify(mockConfig).set("game.proximity-warning.level-3", 3)
        verify(mockPlugin).saveConfig()
    }
    
    @Test
    fun `有効な近接警告レベルは修正されないことを確認`() {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(1)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(3)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(5)
        setupValidUpdateIntervals()
        
        configManager.validateAndFixConfig()
        
        verify(mockConfig, never()).set(eq("game.proximity-warning.level-1"), anyInt())
        verify(mockConfig, never()).set(eq("game.proximity-warning.level-2"), anyInt())
        verify(mockConfig, never()).set(eq("game.proximity-warning.level-3"), anyInt())
    }
    
    @ParameterizedTest
    @ValueSource(doubles = [-1.0, 0.0, 10.1, 15.0, 100.0])
    fun `無効なコンパス更新間隔が修正されることを確認`(invalidInterval: Double) {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        setupValidProximityWarnings()
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(invalidInterval)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(1.0)
        
        configManager.validateAndFixConfig()
        
        verify(mockConfig).set("game.compass-update-interval", 1.0)
        verify(mockPlugin).saveConfig()
        verify(mockLogger).warning("コンパス更新間隔が無効だったため、1秒に修正しました。")
    }
    
    @ParameterizedTest
    @ValueSource(doubles = [-0.5, 0.0, 11.0, 20.0])
    fun `無効な近接チェック間隔が修正されることを確認`(invalidInterval: Double) {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        setupValidProximityWarnings()
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(1.0)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(invalidInterval)
        
        configManager.validateAndFixConfig()
        
        verify(mockConfig).set("game.proximity-check-interval", 1.0)
        verify(mockPlugin).saveConfig()
        verify(mockLogger).warning("近接チェック間隔が無効だったため、1秒に修正しました。")
    }
    
    @ParameterizedTest
    @ValueSource(doubles = [0.1, 1.0, 2.5, 5.0, 10.0])
    fun `有効な更新間隔は修正されないことを確認`(validInterval: Double) {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        setupValidProximityWarnings()
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(validInterval)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(validInterval)
        
        configManager.validateAndFixConfig()
        
        verify(mockConfig, never()).set(eq("game.compass-update-interval"), anyDouble())
        verify(mockConfig, never()).set(eq("game.proximity-check-interval"), anyDouble())
        verify(mockPlugin, never()).saveConfig()
    }
    
    @Test
    fun `複数の無効な設定が同時に修正されることを確認`() {
        // 全ての設定を無効にする
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(0)
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(-1)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(-1)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(-1)
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(-1.0)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(15.0)
        
        configManager.validateAndFixConfig()
        
        // 全ての修正が実行されることを確認
        verify(mockConfig).set("game.min-players", 2)
        verify(mockConfig).set("game.proximity-warning.level-1", 1)
        verify(mockConfig).set("game.proximity-warning.level-2", 2)
        verify(mockConfig).set("game.proximity-warning.level-3", 3)
        verify(mockConfig).set("game.compass-update-interval", 1.0)
        verify(mockConfig).set("game.proximity-check-interval", 1.0)
        
        // saveConfig()が呼ばれることを確認
        verify(mockPlugin, atLeastOnce()).saveConfig()
    }
    
    @Test
    fun `リロード機能の動作を確認`() {
        configManager.reloadConfig()
        
        verify(mockPlugin).reloadConfig()
        // リロード後に検証が実行されることも確認
        verify(mockPlugin, atLeastOnce()).config
    }
    
    @Test
    fun `カスタムメッセージが正しく取得されることを確認`() {
        val customStartMessage = "§aカスタム開始メッセージ"
        val customHunterWin = "§cハンター勝利！"
        val customWarning = "§e危険！"
        
        `when`(mockConfig.getString("messages.game-start")).thenReturn(customStartMessage)
        `when`(mockConfig.getString("messages.hunter-win")).thenReturn(customHunterWin)
        `when`(mockConfig.getString("messages.proximity-warnings.level-1")).thenReturn(customWarning)
        `when`(mockConfig.getString("messages.runner-win")).thenReturn(null) // デフォルト値テスト
        
        assertEquals(customStartMessage, configManager.getGameStartMessage())
        assertEquals(customHunterWin, configManager.getHunterWinMessage())
        assertEquals(customWarning, configManager.getProximityWarningLevel1())
        assertEquals("§a逃げる人の勝利！エンダードラゴンを倒しました！", configManager.getRunnerWinMessage())
    }
    
    @Test
    fun `更新間隔の計算が正しく行われることを確認`() {
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(0.5)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(2.0)
        
        assertEquals(10L, configManager.getCompassUpdateInterval()) // 0.5 * 20 = 10
        assertEquals(40L, configManager.getProximityCheckInterval()) // 2.0 * 20 = 40
    }
    
    private fun setupValidProximityWarnings() {
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(1)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(3)
    }
    
    private fun setupValidUpdateIntervals() {
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(1.0)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(1.0)
    }
}