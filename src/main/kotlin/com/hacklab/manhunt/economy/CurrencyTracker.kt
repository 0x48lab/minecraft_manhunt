package com.hacklab.manhunt.economy

import com.hacklab.manhunt.GameState
import com.hacklab.manhunt.Main
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
    private val lastProximityCheck = mutableMapOf<UUID, Long>()
    private val lastEscapeCheck = mutableMapOf<UUID, Long>()
    private val visitedDimensions = mutableMapOf<UUID, MutableSet<World.Environment>>()
    private val collectedDiamonds = mutableMapOf<UUID, Int>()
    
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
        
        plugin.logger.info("通貨トラッキングを開始しました")
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
        
        plugin.logger.info("通貨トラッキングを停止しました")
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
            economyManager.addMoney(
                player,
                reward,
                EarnReason.Runner.ItemCollected("ダイヤモンド", amount)
            )
        }
    }
}