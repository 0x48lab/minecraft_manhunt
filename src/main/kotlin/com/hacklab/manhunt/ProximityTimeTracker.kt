package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * タイムモードでの接近/エスケープ時間を追跡するクラス
 */
class ProximityTimeTracker(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val messageManager: MessageManager
) {
    // 接近時間の累計（ミリ秒）
    private var hunterProximityTime = 0L
    private var runnerEscapeTime = 0L
    
    // キルボーナス時間（ミリ秒）
    private var hunterBonusTime = 0L
    private var runnerBonusTime = 0L
    
    // 最後の更新時刻
    private var lastUpdateTime = System.currentTimeMillis()
    
    // 追跡タスク
    private var trackingTask: BukkitTask? = null
    
    // プレイヤーごとの接近状態
    private val proximityStates = ConcurrentHashMap<UUID, Boolean>()
    
    // エンカウント通知のクールダウン管理
    // キー: "ハンターUUID:ランナーUUID"、値: 最後の通知時刻
    private val encounterCooldowns = ConcurrentHashMap<String, Long>()
    
    // 前回の接近状態を記録（新しい接近のみ通知するため）
    // キー: "ハンターUUID:ランナーUUID"、値: 接近中かどうか
    private val previousProximityStates = ConcurrentHashMap<String, Boolean>()
    
    /**
     * 追跡を開始
     */
    fun startTracking() {
        stopTracking()
        reset()
        
        trackingTask = object : BukkitRunnable() {
            override fun run() {
                updateProximityTimes()
            }
        }.runTaskTimer(plugin, 0L, 20L) // 1秒ごとに更新
    }
    
    /**
     * 追跡を停止
     */
    fun stopTracking() {
        trackingTask?.cancel()
        trackingTask = null
    }
    
    /**
     * リセット
     */
    fun reset() {
        hunterProximityTime = 0L
        runnerEscapeTime = 0L
        hunterBonusTime = 0L
        runnerBonusTime = 0L
        lastUpdateTime = System.currentTimeMillis()
        proximityStates.clear()
        encounterCooldowns.clear()
        previousProximityStates.clear()
    }
    
    /**
     * キルボーナスを追加
     */
    fun addKillBonus(killerRole: PlayerRole) {
        val bonusMinutes = plugin.getConfigManager().getTimeModeKillBonus()
        val bonusMillis = bonusMinutes * 60 * 1000L
        
        when (killerRole) {
            PlayerRole.HUNTER -> hunterBonusTime += bonusMillis
            PlayerRole.RUNNER -> runnerBonusTime += bonusMillis
            else -> {}
        }
        
        plugin.logger.info("Kill bonus added: ${bonusMinutes}min for $killerRole")
    }
    
    /**
     * 接近/エスケープ時間を更新
     */
    private fun updateProximityTimes() {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastUpdateTime
        lastUpdateTime = currentTime
        
        val proximityDistance = plugin.getConfigManager().getTimeModeProximityDistance()
        val proximityBlocks = proximityDistance * 16.0 // チャンクをブロックに変換
        
        val hunters = gameManager.getAllHunters().filter { it.isOnline && !it.isDead }
        val runners = gameManager.getAllRunners().filter { it.isOnline && !gameManager.isRunnerDead(it) }
        
        // 生存中のランナーがいない場合は更新しない
        if (runners.isEmpty()) return
        
        // ハンターが誰か一人でもランナーに接近しているかチェック
        var anyHunterInProximity = false
        
        // 各ランナーの現在の接近状態を記録（エンカウント検出用）
        val runnerProximityStates = mutableMapOf<UUID, Boolean>()
        for (runner in runners) {
            runnerProximityStates[runner.uniqueId] = false
        }
        
        for (hunter in hunters) {
            var isInProximity = false
            
            for (runner in runners) {
                val encounterKey = "${hunter.uniqueId}:${runner.uniqueId}"
                
                // 同じワールドにいる場合のみチェック
                if (hunter.world == runner.world) {
                    try {
                        val distance = hunter.location.distance(runner.location)
                        if (distance <= proximityBlocks) {
                            isInProximity = true
                            anyHunterInProximity = true
                            runnerProximityStates[runner.uniqueId] = true
                            
                            // エンカウント通知をチェック（新しい接近の場合のみ）
                            val wasInProximity = previousProximityStates[encounterKey] ?: false
                            
                            if (!wasInProximity) {
                                // 新たに接近した場合のみ通知
                                checkAndNotifyEncounter(hunter, runner)
                            }
                            previousProximityStates[encounterKey] = true
                        } else {
                            // 離れた場合は状態をリセット
                            previousProximityStates[encounterKey] = false
                        }
                    } catch (e: Exception) {
                        // 異なるワールド間での距離計算エラーを無視
                        plugin.logger.warning("Distance calculation error between ${hunter.name} and ${runner.name}: ${e.message}")
                        previousProximityStates[encounterKey] = false
                    }
                } else {
                    // 異なるワールドの場合も状態をリセット
                    previousProximityStates[encounterKey] = false
                }
            }
            
            proximityStates[hunter.uniqueId] = isInProximity
        }
        
        // 時間を更新
        if (anyHunterInProximity) {
            // ハンターが接近中
            hunterProximityTime += deltaTime
        } else {
            // ランナーがエスケープ中
            runnerEscapeTime += deltaTime
        }
    }
    
    /**
     * 優勢度を取得（ハンターの優勢度をパーセントで返す）
     */
    fun getHunterDominancePercentage(): Int {
        val totalHunterTime = hunterProximityTime + hunterBonusTime
        val totalRunnerTime = runnerEscapeTime + runnerBonusTime
        val totalTime = totalHunterTime + totalRunnerTime
        
        if (totalTime == 0L) return 50 // 開始直後は50%
        
        return ((totalHunterTime.toDouble() / totalTime) * 100).toInt()
    }
    
    /**
     * 最終結果を取得
     */
    fun getFinalResult(): TimeModeResult {
        val totalHunterTime = hunterProximityTime + hunterBonusTime
        val totalRunnerTime = runnerEscapeTime + runnerBonusTime
        
        return when {
            totalHunterTime > totalRunnerTime -> TimeModeResult.HUNTER_WIN
            totalRunnerTime > totalHunterTime -> TimeModeResult.RUNNER_WIN
            else -> TimeModeResult.DRAW
        }
    }
    
    /**
     * 統計情報を取得
     */
    fun getStatistics(): TimeModeStatistics {
        return TimeModeStatistics(
            hunterProximityTime = hunterProximityTime,
            runnerEscapeTime = runnerEscapeTime,
            hunterBonusTime = hunterBonusTime,
            runnerBonusTime = runnerBonusTime,
            totalHunterTime = hunterProximityTime + hunterBonusTime,
            totalRunnerTime = runnerEscapeTime + runnerBonusTime
        )
    }
    
    /**
     * プレイヤーが接近状態かどうか
     */
    fun isPlayerInProximity(player: Player): Boolean {
        return proximityStates[player.uniqueId] ?: false
    }
    
    /**
     * エンカウント通知をチェックして送信
     */
    private fun checkAndNotifyEncounter(hunter: Player, runner: Player) {
        val encounterKey = "${hunter.uniqueId}:${runner.uniqueId}"
        val currentTime = System.currentTimeMillis()
        val cooldownTime = plugin.getConfigManager().getEncounterCooldown() * 1000L
        
        // クールダウンチェック
        val lastNotification = encounterCooldowns[encounterKey] ?: 0L
        if (currentTime - lastNotification < cooldownTime) {
            return
        }
        
        // エンカウント通知が有効か確認
        if (!plugin.getConfigManager().isEncounterNotificationEnabled()) {
            return
        }
        
        // エンカウント通知を送信
        encounterCooldowns[encounterKey] = currentTime
        
        // 全ハンターに通知
        val hunters = gameManager.getAllHunters().filter { it.isOnline }
        val encounterMessage = messageManager.getMessage("encounter.notification", 
            "hunter" to hunter.name, 
            "runner" to runner.name
        )
        
        for (h in hunters) {
            h.sendMessage(encounterMessage)
            
            // タイトル表示
            if (plugin.getConfigManager().isEncounterTitleEnabled()) {
                val title = messageManager.getMessage(h, "encounter.title")
                val subtitle = messageManager.getMessage(h, "encounter.subtitle", 
                    "hunter" to hunter.name, 
                    "runner" to runner.name
                )
                h.sendTitle(title, subtitle, 10, 40, 10)
            }
            
            // 音声通知
            if (plugin.getConfigManager().isEncounterSoundEnabled()) {
                h.playSound(h.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f)
            }
        }
        
        plugin.logger.info("Encounter notification sent: ${hunter.name} found ${runner.name}")
    }
}

/**
 * タイムモードの結果
 */
enum class TimeModeResult {
    HUNTER_WIN,
    RUNNER_WIN,
    DRAW
}

/**
 * タイムモードの統計情報
 */
data class TimeModeStatistics(
    val hunterProximityTime: Long,
    val runnerEscapeTime: Long,
    val hunterBonusTime: Long,
    val runnerBonusTime: Long,
    val totalHunterTime: Long,
    val totalRunnerTime: Long
) {
    fun getHunterProximityMinutes(): Int = (hunterProximityTime / 60000).toInt()
    fun getRunnerEscapeMinutes(): Int = (runnerEscapeTime / 60000).toInt()
    fun getHunterBonusMinutes(): Int = (hunterBonusTime / 60000).toInt()
    fun getRunnerBonusMinutes(): Int = (runnerBonusTime / 60000).toInt()
    fun getTotalHunterMinutes(): Int = (totalHunterTime / 60000).toInt()
    fun getTotalRunnerMinutes(): Int = (totalRunnerTime / 60000).toInt()
}