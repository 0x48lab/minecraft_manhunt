package com.hacklab.manhunt

import com.hacklab.manhunt.economy.CurrencyConfig
import com.hacklab.manhunt.economy.MovementConfig
import org.bukkit.configuration.file.FileConfiguration

class ConfigManager(private val plugin: Main) {
    private val config: FileConfiguration = plugin.config
    
    // ゲーム設定
    fun getMinPlayers(): Int = config.getInt("game.min-players", 2)
    fun isAutoStartEnabled(): Boolean = config.getBoolean("game.auto-start", false)
    
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
    
    // 実績リセット設定
    fun shouldResetAdvancements(): Boolean = config.getBoolean("game.reset-advancements", true)
    
    // 夜スキップ設定
    fun isNightSkipEnabled(): Boolean = config.getBoolean("game.night-skip.enabled", true)
    
    // 距離表示設定
    fun getMinimumDisplayDistance(): Int = config.getInt("ui.distance-display.minimum-distance", 5)
    
    // 名前タグ設定
    fun isHideNameTagsDuringGame(): Boolean = config.getBoolean("game.name-tag.hide-during-game", true)
    fun getNameTagVisibilityMode(): String = config.getString("game.name-tag.visibility-mode", "all") ?: "all"
    
    // 初期装備設定
    fun getStartingItems(role: PlayerRole): List<String> {
        val roleName = when (role) {
            PlayerRole.RUNNER -> "runner"
            PlayerRole.HUNTER -> "hunter"
            PlayerRole.SPECTATOR -> return emptyList()
        }
        return config.getStringList("game.starting-items.$roleName")
    }
    
    // ショップアイテム設定
    fun isShopItemEnabled(): Boolean = config.getBoolean("shop.item-enabled", true)
    
    // PVP設定
    fun isFriendlyFireDisabled(): Boolean = config.getBoolean("game.pvp.disable-friendly-fire", false)
    
    // ゲームモード設定
    fun getTimeLimit(): Int = config.getInt("game.time-limit", 0)
    fun isTimeLimitMode(): Boolean = getTimeLimit() > 0
    fun getTimeModeKillBonus(): Int = config.getInt("game.time-mode.kill-bonus", 5)
    fun getTimeModeProximityDistance(): Int = config.getInt("game.time-mode.proximity-distance", 3)
    fun isRunnerInstantRespawnInTimeMode(): Boolean = config.getBoolean("game.time-mode.runner-instant-respawn", true)
    fun getTimeModeRunnerRespawnTime(): Int = config.getInt("game.time-mode.runner-respawn-time", 30)
    
    // エンカウント通知設定
    fun isEncounterNotificationEnabled(): Boolean = config.getBoolean("game.encounter.enabled", true)
    fun getEncounterCooldown(): Int = config.getInt("game.encounter.cooldown", 60)
    fun isEncounterSoundEnabled(): Boolean = config.getBoolean("game.encounter.sound", true)
    fun isEncounterTitleEnabled(): Boolean = config.getBoolean("game.encounter.title", true)
    
    // スポーン設定
    fun getSpawnMinRadius(): Double = config.getDouble("game.spawn.min-radius", 100.0)
    fun getSpawnMaxRadius(): Double = config.getDouble("game.spawn.max-radius", 2000.0)
    fun getSpawnEnemyMinDistance(): Double = config.getDouble("game.spawn.enemy-min-distance", 500.0)
    
    data class TeamSpreadConfig(
        val enabled: Boolean = true,
        val lowThreshold: Double = 1.0,
        val mediumThreshold: Double = 1.5,
        val highThreshold: Double = 2.0,
        val lowDistance: Double = 0.0,
        val mediumDistance: Double = 1000.0,
        val highDistance: Double = 2000.0
    )
    
    fun getSpawnTeamSpreadConfig(): TeamSpreadConfig {
        val spreadSection = config.getConfigurationSection("game.spawn.team-spread") ?: return TeamSpreadConfig()
        
        return TeamSpreadConfig(
            enabled = spreadSection.getBoolean("enabled", true),
            lowThreshold = spreadSection.getDouble("low-threshold", 1.0),
            mediumThreshold = spreadSection.getDouble("medium-threshold", 1.5),
            highThreshold = spreadSection.getDouble("high-threshold", 2.0),
            lowDistance = spreadSection.getDouble("low-distance", 0.0),
            mediumDistance = spreadSection.getDouble("medium-distance", 1000.0),
            highDistance = spreadSection.getDouble("high-distance", 2000.0)
        )
    }
    
    
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
        
        // スポーン設定の検証
        val spawnMinRadius = getSpawnMinRadius()
        val spawnMaxRadius = getSpawnMaxRadius()
        val spawnEnemyMinDistance = getSpawnEnemyMinDistance()
        
        if (spawnMinRadius < 0 || spawnMinRadius > spawnMaxRadius) {
            config.set("game.spawn.min-radius", 100.0)
            plugin.logger.warning("スポーン最小半径が無効だったため、100メートルに修正しました。")
            needsSave = true
        }
        
        if (spawnMaxRadius < spawnMinRadius || spawnMaxRadius > 5000) {
            config.set("game.spawn.max-radius", 2000.0)
            plugin.logger.warning("スポーン最大半径が無効だったため、2000メートルに修正しました。")
            needsSave = true
        }
        
        if (spawnEnemyMinDistance < 0 || spawnEnemyMinDistance > spawnMaxRadius) {
            config.set("game.spawn.enemy-min-distance", 500.0)
            plugin.logger.warning("敵同士の最小距離が無効だったため、500メートルに修正しました。")
            needsSave = true
        }
        
        // チーム分散設定の検証
        val teamSpread = getSpawnTeamSpreadConfig()
        if (teamSpread.lowThreshold < 0 || teamSpread.mediumThreshold < teamSpread.lowThreshold || 
            teamSpread.highThreshold < teamSpread.mediumThreshold) {
            config.set("game.spawn.team-spread.low-threshold", 1.0)
            config.set("game.spawn.team-spread.medium-threshold", 1.5)
            config.set("game.spawn.team-spread.high-threshold", 2.0)
            plugin.logger.warning("チーム分散の閾値が無効だったため、デフォルト値に修正しました。")
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
            hunterTrackingReward = economySection.getInt("hunter.tracking-reward", 30),
            hunterTrackingDistance = economySection.getInt("hunter.tracking-distance", 100),
            hunterTrackingDuration = economySection.getInt("hunter.tracking-duration", 30),
            hunterTrackingCooldown = economySection.getInt("hunter.tracking-cooldown", 60),
            
            // ランナー設定
            runnerSurvivalBonus = economySection.getDouble("runner.survival-bonus", 0.5),
            runnerSurvivalInterval = economySection.getInt("runner.survival-interval", 5),
            runnerNetherReward = economySection.getInt("runner.nether-reward", 1000),
            runnerFortressReward = economySection.getInt("runner.fortress-reward", 1500),
            runnerEndReward = economySection.getInt("runner.end-reward", 2000),
            runnerDiamondReward = economySection.getInt("runner.diamond-reward", 100),
            runnerEscapeReward = economySection.getInt("runner.escape-reward", 50),
            runnerEscapeDistance = economySection.getInt("runner.escape-distance", 100),
            advancementReward = economySection.getInt("runner.advancement-reward", 10),
            
            // 共通設定（config.ymlの実際の値に合わせてデフォルト値を修正）
            startingBalance = economySection.getInt("starting-balance", 0),
            maxBalance = economySection.getInt("max-balance", 999999),
            currencyUnit = economySection.getString("currency-unit", "G") ?: "G",
            earnMultiplier = economySection.getDouble("earn-multiplier", 2.0)
        )
    }
    
    // 移動報酬設定を取得
    fun getMovementConfig(): MovementConfig {
        val movementSection = config.getConfigurationSection("movement") ?: return MovementConfig()
        
        return MovementConfig(
            sprintRewardPerBlock = movementSection.getDouble("sprint-reward-per-block", 0.2),
            sprintMaxRewardPerMinute = movementSection.getInt("sprint-max-reward-per-minute", 50),
            sprintRewardCooldown = movementSection.getInt("sprint-reward-cooldown", 1)
        )
    }
}