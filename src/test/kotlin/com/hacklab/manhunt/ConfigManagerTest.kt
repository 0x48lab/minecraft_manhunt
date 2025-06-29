package com.hacklab.manhunt

import org.bukkit.configuration.file.FileConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

/**
 * ConfigManagerの単体テスト
 */
class ConfigManagerTest {
    
    private lateinit var mockPlugin: Main
    private lateinit var mockConfig: FileConfiguration
    private lateinit var configManager: ConfigManager
    
    @BeforeEach
    fun setUp() {
        mockPlugin = mock(Main::class.java)
        mockConfig = mock(FileConfiguration::class.java)
        
        // プラグインのconfigを返すように設定
        `when`(mockPlugin.config).thenReturn(mockConfig)
        
        configManager = ConfigManager(mockPlugin)
    }
    
    @Test
    fun `getMinPlayersがデフォルト値を返すことを確認`() {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        
        assertEquals(2, configManager.getMinPlayers())
        verify(mockConfig).getInt("game.min-players", 2)
    }
    
    @Test
    fun `getMinPlayersがカスタム値を返すことを確認`() {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(5)
        
        assertEquals(5, configManager.getMinPlayers())
    }
    
    @Test
    fun `近接警告レベルが正しく取得されることを確認`() {
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(1)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(3)
        
        assertEquals(1, configManager.getProximityLevel1())
        assertEquals(2, configManager.getProximityLevel2())
        assertEquals(3, configManager.getProximityLevel3())
    }
    
    @Test
    fun `更新間隔が正しく取得されることを確認`() {
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(1.5)
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(2.0)
        
        assertEquals(30L, configManager.getCompassUpdateInterval()) // 1.5 * 20 = 30
        assertEquals(40L, configManager.getProximityCheckInterval()) // 2.0 * 20 = 40
    }
    
    @Test
    fun `メッセージがデフォルト値を返すことを確認`() {
        `when`(mockConfig.getString("messages.game-start")).thenReturn(null)
        
        assertEquals("§6[Manhunt] ゲーム開始！", configManager.getGameStartMessage())
    }
    
    @Test
    fun `メッセージがカスタム値を返すことを確認`() {
        `when`(mockConfig.getString("messages.game-start")).thenReturn("§aカスタムゲーム開始！")
        
        assertEquals("§aカスタムゲーム開始！", configManager.getGameStartMessage())
    }
    
    @Test
    fun `近接警告メッセージが正しく取得されることを確認`() {
        `when`(mockConfig.getString("messages.proximity-warnings.level-1")).thenReturn("§cカスタム警告1")
        `when`(mockConfig.getString("messages.proximity-warnings.level-2")).thenReturn(null)
        
        assertEquals("§cカスタム警告1", configManager.getProximityWarningLevel1())
        assertEquals("§6§l[警告] 追う人が2チャンク以内にいます！", configManager.getProximityWarningLevel2())
    }
    
    @Test
    fun `設定値検証で無効な最小プレイヤー数が修正されることを確認`() {
        // 無効な値（1未満）を返すように設定
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(1)
        `when`(mockPlugin.logger).thenReturn(mock(java.util.logging.Logger::class.java))
        
        configManager.validateAndFixConfig()
        
        // 修正されることを確認
        verify(mockConfig).set("game.min-players", 2)
        verify(mockPlugin).saveConfig()
    }
    
    @Test
    fun `設定値検証で無効な近接警告レベルが修正されることを確認`() {
        // レベルの順序が逆の場合
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(3)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(1)
        `when`(mockPlugin.logger).thenReturn(mock(java.util.logging.Logger::class.java))
        
        configManager.validateAndFixConfig()
        
        // デフォルト値に修正されることを確認
        verify(mockConfig).set("game.proximity-warning.level-1", 1)
        verify(mockConfig).set("game.proximity-warning.level-2", 2)
        verify(mockConfig).set("game.proximity-warning.level-3", 3)
        verify(mockPlugin).saveConfig()
    }
    
    @Test
    fun `設定値検証で無効な更新間隔が修正されることを確認`() {
        `when`(mockConfig.getInt("game.min-players", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-1", 1)).thenReturn(1)
        `when`(mockConfig.getInt("game.proximity-warning.level-2", 2)).thenReturn(2)
        `when`(mockConfig.getInt("game.proximity-warning.level-3", 3)).thenReturn(3)
        `when`(mockConfig.getDouble("game.compass-update-interval", 1.0)).thenReturn(-1.0) // 無効な値
        `when`(mockConfig.getDouble("game.proximity-check-interval", 1.0)).thenReturn(15.0) // 無効な値（上限超過）
        `when`(mockPlugin.logger).thenReturn(mock(java.util.logging.Logger::class.java))
        
        configManager.validateAndFixConfig()
        
        // デフォルト値に修正されることを確認
        verify(mockConfig).set("game.compass-update-interval", 1.0)
        verify(mockConfig).set("game.proximity-check-interval", 1.0)
        verify(mockPlugin, times(2)).saveConfig()
    }
    
    @Test
    fun `リロード機能が正しく動作することを確認`() {
        `when`(mockPlugin.logger).thenReturn(mock(java.util.logging.Logger::class.java))
        
        configManager.reloadConfig()
        
        verify(mockPlugin).reloadConfig()
    }
}