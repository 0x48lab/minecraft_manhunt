package com.hacklab.manhunt.economy

import com.hacklab.manhunt.GameState
import com.hacklab.manhunt.Main
import com.hacklab.manhunt.MessageManager
import com.hacklab.manhunt.PlayerRole
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

/**
 * プレイヤーの行動を追跡して通貨を付与するクラス
 */
class CurrencyTracker(
    private val plugin: Main,
    private val economyManager: EconomyManager,
    private val config: CurrencyConfig
) {
    private val messageManager: MessageManager
        get() = plugin.getMessageManager()
    private val lastProximityCheck = mutableMapOf<UUID, Long>()
    private val lastEscapeCheck = mutableMapOf<UUID, Long>()
    private val visitedDimensions = mutableMapOf<UUID, MutableSet<World.Environment>>()
    private val collectedDiamonds = mutableMapOf<UUID, Int>()
    private val lastSprintReward = mutableMapOf<UUID, Long>()
    private val sprintRewardThisMinute = mutableMapOf<UUID, Int>()
    
    private var timeBonusTask: BukkitRunnable? = null
    
    /**
     * 通貨トラッキングを開始
     */
    fun startTracking() {
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) return
        
        // 既存のタスクをキャンセル
        stopTracking()
        
        // 時間ボーナスタスクを開始
        startTimeBonusTask()
        
        plugin.logger.info("Currency tracking started")
    }
    
    /**
     * 通貨トラッキングを停止
     */
    fun stopTracking() {
        timeBonusTask?.cancel()
        timeBonusTask = null
        
        // データをクリア
        lastProximityCheck.clear()
        lastEscapeCheck.clear()
        visitedDimensions.clear()
        collectedDiamonds.clear()
        lastSprintReward.clear()
        sprintRewardThisMinute.clear()
        
        plugin.logger.info("Currency tracking stopped")
    }
    
    /**
     * 時間ボーナスタスクを開始
     */
    private fun startTimeBonusTask() {
        timeBonusTask = object : BukkitRunnable() {
            override fun run() {
                if (plugin.getGameManager().getGameState() != GameState.RUNNING) {
                    cancel()
                    return
                }
                
                // ハンターの時間ボーナス
                plugin.getGameManager().getAllHunters().forEach { hunter ->
                    if (hunter.isOnline && !hunter.isDead) {
                        val amount = (config.hunterTimeBonus * config.hunterTimeBonusInterval).toInt()
                        economyManager.addMoney(hunter, amount, EarnReason.Hunter.TimeBonus)
                    }
                }
                
                // ランナーの生存ボーナス
                plugin.getGameManager().getAllRunners().forEach { runner ->
                    if (runner.isOnline && !runner.isDead) {
                        val amount = (config.runnerSurvivalBonus * config.runnerSurvivalInterval).toInt()
                        economyManager.addMoney(runner, amount, EarnReason.Runner.SurvivalBonus)
                        
                        // 逃走成功チェック
                        checkEscapeBonus(runner)
                    }
                }
                
                // 接近ボーナスは削除（ログが多すぎるため）
                // checkProximityBonus()
            }
        }
        
        // ハンターとランナーで異なる間隔を使用
        val interval = config.runnerSurvivalInterval.coerceAtMost(config.hunterTimeBonusInterval)
        timeBonusTask?.runTaskTimer(plugin, 0L, (interval * 20).toLong())
    }
    
    /**
     * 接近ボーナスをチェック
     */
    private fun checkProximityBonus() {
        val hunters = plugin.getGameManager().getAllHunters().filter { it.isOnline && !it.isDead }
        val runners = plugin.getGameManager().getAllRunners().filter { it.isOnline && !it.isDead }
        
        hunters.forEach { hunter ->
            runners.forEach { runner ->
                if (hunter.world == runner.world) {
                    val distance = hunter.location.distance(runner.location)
                    if (distance <= config.hunterProximityDistance) {
                        val currentTime = System.currentTimeMillis()
                        val lastCheck = lastProximityCheck[hunter.uniqueId] ?: 0L
                        
                        // クールダウン（30秒）
                        if (currentTime - lastCheck >= 30000) {
                            economyManager.addMoney(
                                hunter, 
                                config.hunterProximityReward, 
                                EarnReason.Hunter.Proximity(distance.toInt())
                            )
                            lastProximityCheck[hunter.uniqueId] = currentTime
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 逃走成功ボーナスをチェック
     */
    private fun checkEscapeBonus(runner: Player) {
        val hunters = plugin.getGameManager().getAllHunters().filter { it.isOnline && !it.isDead }
        
        val nearestDistance = hunters
            .filter { it.world == runner.world }
            .minOfOrNull { it.location.distance(runner.location) } ?: Double.MAX_VALUE
        
        if (nearestDistance >= config.runnerEscapeDistance) {
            val currentTime = System.currentTimeMillis()
            val lastCheck = lastEscapeCheck[runner.uniqueId] ?: 0L
            
            // クールダウン（60秒）
            if (currentTime - lastCheck >= 60000) {
                economyManager.addMoney(runner, config.runnerEscapeReward, EarnReason.Runner.EscapeBonus)
                lastEscapeCheck[runner.uniqueId] = currentTime
            }
        }
    }
    
    /**
     * ダメージを与えた時の処理
     */
    fun onDamageDealt(attacker: Player, victim: Player, damage: Double) {
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) return
        
        val attackerRole = plugin.getGameManager().getPlayerRole(attacker)
        val victimRole = plugin.getGameManager().getPlayerRole(victim)
        
        if (attackerRole == PlayerRole.HUNTER && victimRole == PlayerRole.RUNNER) {
            val amount = (damage * config.hunterDamageReward).toInt()
            economyManager.addMoney(attacker, amount, EarnReason.Hunter.DamageDealt(damage.toInt()))
        }
    }
    
    /**
     * プレイヤーがキルされた時の処理
     */
    fun onPlayerKill(killer: Player, victim: Player) {
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) return
        
        val killerRole = plugin.getGameManager().getPlayerRole(killer)
        val victimRole = plugin.getGameManager().getPlayerRole(victim)
        
        if (killerRole == PlayerRole.HUNTER && victimRole == PlayerRole.RUNNER) {
            economyManager.addMoney(killer, config.hunterKillReward, EarnReason.Hunter.Kill(victim.name))
        }
    }
    
    /**
     * ディメンション変更時の処理
     */
    fun onDimensionChange(player: Player, environment: World.Environment) {
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) return
        
        val role = plugin.getGameManager().getPlayerRole(player)
        if (role != PlayerRole.RUNNER) return
        
        val visited = visitedDimensions.getOrPut(player.uniqueId) { mutableSetOf() }
        
        if (!visited.contains(environment)) {
            visited.add(environment)
            
            when (environment) {
                World.Environment.NETHER -> {
                    economyManager.addMoney(
                        player, 
                        config.runnerNetherReward, 
                        EarnReason.Runner.Progress(ProgressType.NETHER_ENTER)
                    )
                }
                World.Environment.THE_END -> {
                    economyManager.addMoney(
                        player, 
                        config.runnerEndReward, 
                        EarnReason.Runner.Progress(ProgressType.END_ENTER)
                    )
                }
                else -> {}
            }
        }
    }
    
    /**
     * 要塞発見時の処理
     */
    fun onFortressFound(player: Player) {
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) return
        
        val role = plugin.getGameManager().getPlayerRole(player)
        if (role != PlayerRole.RUNNER) return
        
        val visited = visitedDimensions.getOrPut(player.uniqueId) { mutableSetOf() }
        
        // 仮の実装：要塞発見フラグ
        if (!visited.contains(World.Environment.CUSTOM)) { // CUSTOMを要塞フラグとして使用
            visited.add(World.Environment.CUSTOM)
            economyManager.addMoney(
                player,
                config.runnerFortressReward,
                EarnReason.Runner.Progress(ProgressType.FORTRESS_FOUND)
            )
        }
    }
    
    /**
     * アイテム収集時の処理
     */
    fun onItemCollected(player: Player, material: Material, amount: Int) {
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) return
        
        val role = plugin.getGameManager().getPlayerRole(player)
        if (role != PlayerRole.RUNNER) return
        
        if (material == Material.DIAMOND) {
            val collected = collectedDiamonds.getOrPut(player.uniqueId) { 0 }
            collectedDiamonds[player.uniqueId] = collected + amount
            
            val reward = config.runnerDiamondReward * amount
            val diamondName = messageManager.getMessage(player, "materials.diamond")
            economyManager.addMoney(
                player,
                reward,
                EarnReason.Runner.ItemCollected(diamondName, amount)
            )
            
            // Record diamond collection for statistics
            plugin.getGameManager().recordDiamondCollected(player, amount)
        }
    }
    
    /**
     * スプリント移動時の処理（3チャンク以内に敵がいる場合のみ）
     */
    fun onSprintMovement(player: Player, distance: Double) {
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) return
        if (distance <= 0 || !player.isSprinting) return
        
        val role = plugin.getGameManager().getPlayerRole(player)
        if (role == null || role == PlayerRole.SPECTATOR) return
        
        // 3チャンク以内に敵がいるかチェック
        if (!isEnemyWithinProximity(player, role)) return
        
        val currentTime = System.currentTimeMillis()
        val lastReward = lastSprintReward[player.uniqueId] ?: 0L
        val currentMinute = currentTime / 60000  // 分単位
        val lastRewardMinute = lastReward / 60000
        
        // 分が変わったらカウンターリセット
        if (currentMinute != lastRewardMinute) {
            sprintRewardThisMinute[player.uniqueId] = 0
        }
        
        // クールダウンチェック（1秒）
        if (currentTime - lastReward < 1000) return
        
        // 1分間の最大報酬チェック
        val rewardThisMinute = sprintRewardThisMinute[player.uniqueId] ?: 0
        val maxRewardPerMinute = plugin.getConfigManager().getMovementConfig().sprintMaxRewardPerMinute
        
        if (rewardThisMinute >= maxRewardPerMinute) return
        
        // 報酬計算
        val rewardPerBlock = plugin.getConfigManager().getMovementConfig().sprintRewardPerBlock
        val rawReward = (distance * rewardPerBlock).toInt()
        val actualReward = (rewardThisMinute + rawReward).coerceAtMost(maxRewardPerMinute) - rewardThisMinute
        
        if (actualReward > 0) {
            economyManager.addMoney(player, actualReward, EarnReason.Movement.Sprint(distance.toInt()))
            lastSprintReward[player.uniqueId] = currentTime
            sprintRewardThisMinute[player.uniqueId] = rewardThisMinute + actualReward
        }
    }
    
    /**
     * 3チャンク以内に敵がいるかチェック
     */
    private fun isEnemyWithinProximity(player: Player, role: PlayerRole): Boolean {
        val configManager = plugin.getConfigManager()
        val proximityDistance = configManager.getProximityLevel3() * 16 // 3チャンク = 48ブロック
        
        // プレイヤーの役割に応じて敵を特定
        val enemies = when (role) {
            PlayerRole.HUNTER -> plugin.getGameManager().getAllRunners()
            PlayerRole.RUNNER -> plugin.getGameManager().getAllHunters()
            PlayerRole.SPECTATOR -> return false
        }
        
        // 生存している敵との距離をチェック
        return enemies.any { enemy ->
            enemy.isOnline && 
            !enemy.isDead && 
            enemy.world == player.world &&
            enemy.location.distance(player.location) <= proximityDistance
        }
    }
}