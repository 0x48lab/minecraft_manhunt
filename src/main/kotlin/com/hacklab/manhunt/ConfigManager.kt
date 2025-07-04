package com.hacklab.manhunt

import com.hacklab.manhunt.economy.CurrencyConfig
import org.bukkit.configuration.file.FileConfiguration

class ConfigManager(private val plugin: Main) {
    private val config: FileConfiguration = plugin.config
    
    // ゲーム設定
    fun getMinPlayers(): Int = config.getInt("game.min-players", 2)
    
    fun getProximityLevel1(): Int = config.getInt("game.proximity-warning.level-1", 1)
    fun getProximityLevel2(): Int = config.getInt("game.proximity-warning.level-2", 2)
    fun getProximityLevel3(): Int = config.getInt("game.proximity-warning.level-3", 3)
    fun getProximityCooldown(): Int = config.getInt("game.proximity-warning.cooldown-seconds", 5)
    
    fun getCompassUpdateInterval(): Long = (config.getDouble("game.compass-update-interval", 1.0) * 20).toLong()
    fun getProximityCheckInterval(): Long = (config.getDouble("game.proximity-check-interval", 1.0) * 20).toLong()
    fun getStartCountdown(): Int = config.getInt("game.start-countdown", 10)
    
    // UI設定
    fun isScoreboardEnabled(): Boolean = config.getBoolean("ui.scoreboard-enabled", true)
    fun isActionBarEnabled(): Boolean = config.getBoolean("ui.actionbar-enabled", true)
    fun isBossBarEnabled(): Boolean = config.getBoolean("ui.bossbar-enabled", true)
    fun isTitleEnabled(): Boolean = config.getBoolean("ui.title-enabled", true)
    
    fun getScoreboardUpdateInterval(): Long = (config.getDouble("ui.scoreboard-update-interval", 1.0) * 20).toLong()
    fun getActionBarUpdateInterval(): Long = (config.getDouble("ui.actionbar-update-interval", 2.0) * 20).toLong()
    
    // リスポン設定
    fun getRunnerRespawnTime(): Int = config.getInt("game.respawn.runner-respawn-time", 10)
    fun isHunterInstantRespawn(): Boolean = config.getBoolean("game.respawn.hunter-instant-respawn", true)
    
    // 距離表示設定
    fun getMinimumDisplayDistance(): Int = config.getInt("ui.distance-display.minimum-distance", 5)
    
    // 名前タグ設定
    fun isHideNameTagsDuringGame(): Boolean = config.getBoolean("game.name-tag.hide-during-game", true)
    fun getNameTagVisibilityMode(): String = config.getString("game.name-tag.visibility-mode", "all") ?: "all"
    
    
    // 設定値の検証と修正
    fun validateAndFixConfig() {
        var needsSave = false
        
        // 最小プレイヤー数の検証
        val minPlayers = getMinPlayers()
        if (minPlayers < 2) {
            config.set("game.min-players", 2)
            plugin.logger.warning("最小プレイヤー数が無効だったため、2に修正しました。")
            needsSave = true
        }
        
        // 近接警告レベルの検証
        val level1 = getProximityLevel1()
        val level2 = getProximityLevel2()
        val level3 = getProximityLevel3()
        
        if (level1 <= 0 || level2 <= 0 || level3 <= 0) {
            config.set("game.proximity-warning.level-1", 1)
            config.set("game.proximity-warning.level-2", 2)
            config.set("game.proximity-warning.level-3", 3)
            plugin.logger.warning("近接警告レベルが無効だったため、デフォルト値に修正しました。")
            needsSave = true
        }
        
        if (level1 >= level2 || level2 >= level3) {
            config.set("game.proximity-warning.level-1", 1)
            config.set("game.proximity-warning.level-2", 2)
            config.set("game.proximity-warning.level-3", 3)
            plugin.logger.warning("近接警告レベルの順序が無効だったため、デフォルト値に修正しました。")
            needsSave = true
        }
        
        // 更新間隔の検証
        val compassInterval = config.getDouble("game.compass-update-interval", 1.0)
        val proximityInterval = config.getDouble("game.proximity-check-interval", 1.0)
        
        if (compassInterval <= 0 || compassInterval > 10) {
            config.set("game.compass-update-interval", 1.0)
            plugin.logger.warning("コンパス更新間隔が無効だったため、1秒に修正しました。")
            needsSave = true
        }
        
        if (proximityInterval <= 0 || proximityInterval > 10) {
            config.set("game.proximity-check-interval", 1.0)
            plugin.logger.warning("近接チェック間隔が無効だったため、1秒に修正しました。")
            needsSave = true
        }
        
        // 距離表示設定の検証
        val minDisplayDistance = getMinimumDisplayDistance()
        if (minDisplayDistance < 1 || minDisplayDistance > 100) {
            config.set("ui.distance-display.minimum-distance", 5)
            plugin.logger.warning("最小表示距離が無効だったため、5メートルに修正しました。")
            needsSave = true
        }
        
        if (needsSave) {
            plugin.saveConfig()
        }
    }
    
    // 設定値をリロード
    fun reloadConfig() {
        plugin.reloadConfig()
        validateAndFixConfig()
    }
    
    // 通貨設定を取得
    fun getCurrencyConfig(): CurrencyConfig {
        val economySection = config.getConfigurationSection("economy") ?: return CurrencyConfig()
        
        return CurrencyConfig(
            // ハンター設定（config.ymlの実際の値に合わせてデフォルト値を修正）
            hunterDamageReward = economySection.getInt("hunter.damage-reward", 55),
            hunterKillReward = economySection.getInt("hunter.kill-reward", 500),
            hunterProximityReward = economySection.getInt("hunter.proximity-reward", 100),
            hunterProximityDistance = economySection.getInt("hunter.proximity-distance", 50),
            hunterTimeBonus = economySection.getDouble("hunter.time-bonus", 0.3),
            hunterTimeBonusInterval = economySection.getInt("hunter.time-bonus-interval", 10),
            
            // ランナー設定
            runnerSurvivalBonus = economySection.getDouble("runner.survival-bonus", 0.5),
            runnerSurvivalInterval = economySection.getInt("runner.survival-interval", 5),
            runnerNetherReward = economySection.getInt("runner.nether-reward", 1000),
            runnerFortressReward = economySection.getInt("runner.fortress-reward", 1500),
            runnerEndReward = economySection.getInt("runner.end-reward", 2000),
            runnerDiamondReward = economySection.getInt("runner.diamond-reward", 100),
            runnerEscapeReward = economySection.getInt("runner.escape-reward", 50),
            runnerEscapeDistance = economySection.getInt("runner.escape-distance", 100),
            
            // 共通設定（config.ymlの実際の値に合わせてデフォルト値を修正）
            startingBalance = economySection.getInt("starting-balance", 0),
            maxBalance = economySection.getInt("max-balance", 999999),
            currencyUnit = economySection.getString("currency-unit", "G") ?: "G"
        )
    }
}