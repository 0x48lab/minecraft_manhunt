package com.hacklab.manhunt

import org.bukkit.*
import org.bukkit.entity.EntityType
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.FireworkMeta
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * ゲーム結果表示とリザルト演出を管理するクラス
 */
class GameResultManager(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val messageManager: MessageManager
) {
    
    private var fireworkTask: BukkitTask? = null
    
    /**
     * ゲーム終了時のリザルト表示
     */
    fun showGameResult(gameStats: GameStats) {
        val winningTeam = gameStats.getWinningTeam()
        val mvp = gameStats.getMVP()
        val gameDuration = gameStats.getGameDuration()
        
        // 1. 基本勝利メッセージ
        showWinMessage(winningTeam, gameStats.getWinCondition())
        
        // 2. 視覚効果の開始
        startVictoryEffects(winningTeam)
        
        // 3. 詳細統計を3秒後に表示
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            showDetailedStats(gameStats, gameDuration)
        }, 60L) // 3秒後
        
        // 4. MVP発表を6秒後に表示
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            showMVPAnnouncement(mvp, winningTeam)
        }, 120L) // 6秒後
        
        // 5. 個人成績を9秒後に表示
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            showPersonalStats(gameStats)
        }, 180L) // 9秒後
        
        // 6. 花火演出を12秒後に停止
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            stopVictoryEffects()
        }, 240L) // 12秒後
    }
    
    /**
     * 勝利メッセージ表示
     */
    private fun showWinMessage(winningTeam: PlayerRole?, winCondition: GameStats.WinCondition?) {
        val winMessage = when (winCondition) {
            GameStats.WinCondition.ENDER_DRAGON_KILLED -> messageManager.getMessage("result.victory.dragon-killed")
            GameStats.WinCondition.ALL_RUNNERS_ELIMINATED -> messageManager.getMessage("result.victory.all-runners-eliminated")
            GameStats.WinCondition.ALL_HUNTERS_LEFT -> messageManager.getMessage("result.victory.all-hunters-left")
            GameStats.WinCondition.ALL_RUNNERS_LEFT -> messageManager.getMessage("result.victory.all-runners-left")
            else -> messageManager.getMessage("result.victory.game-end")
        }
        
        // 全体ブロードキャスト
        val separator = messageManager.getMessage("result.header.separator").repeat(50)
        val title = messageManager.getMessage("result.header.title")
        
        Bukkit.broadcastMessage("")
        Bukkit.broadcastMessage("§6§l$separator")
        Bukkit.broadcastMessage("§6§l$title")
        Bukkit.broadcastMessage("§6§l$separator")
        Bukkit.broadcastMessage(winMessage)
        Bukkit.broadcastMessage("§6§l$separator")
        Bukkit.broadcastMessage("")
        
        // タイトル表示
        val titleColor = when (winningTeam) {
            PlayerRole.HUNTER -> "§c§l"
            PlayerRole.RUNNER -> "§a§l"
            else -> "§6§l"
        }
        
        val titleText = when (winningTeam) {
            PlayerRole.HUNTER -> messageManager.getMessage("result.title.hunters-win")
            PlayerRole.RUNNER -> messageManager.getMessage("result.title.runners-win")
            else -> messageManager.getMessage("result.title.game-end")
        }
        
        val subtitleText = when (winCondition) {
            GameStats.WinCondition.ENDER_DRAGON_KILLED -> messageManager.getMessage("result.subtitle.dragon-defeated")
            GameStats.WinCondition.ALL_RUNNERS_ELIMINATED -> messageManager.getMessage("result.subtitle.all-eliminated")
            GameStats.WinCondition.ALL_HUNTERS_LEFT -> messageManager.getMessage("result.subtitle.hunters-abandoned")
            GameStats.WinCondition.ALL_RUNNERS_LEFT -> messageManager.getMessage("result.subtitle.runners-abandoned")
            else -> messageManager.getMessage("result.subtitle.game-complete")
        }
        
        // 全プレイヤーにタイトル表示
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendTitle(titleColor + titleText, subtitleText, 10, 100, 20)
            
            // 勝利音効果
            if (gameManager.getPlayerRole(player) == winningTeam) {
                player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
            } else {
                player.playSound(player.location, Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f)
            }
        }
    }
    
    /**
     * 詳細統計表示
     */
    private fun showDetailedStats(gameStats: GameStats, gameDuration: String) {
        val hunterStats = gameStats.getTeamStats(PlayerRole.HUNTER)
        val runnerStats = gameStats.getTeamStats(PlayerRole.RUNNER)
        
        Bukkit.broadcastMessage(messageManager.getMessage("result.stats.header"))
        Bukkit.broadcastMessage(messageManager.getMessage("result.stats.game-duration", mapOf("duration" to gameDuration)))
        Bukkit.broadcastMessage("")
        
        // ハンター統計
        if (hunterStats.isNotEmpty()) {
            Bukkit.broadcastMessage(messageManager.getMessage("result.stats.hunter-stats-header"))
            hunterStats.forEachIndexed { index, stats ->
                val rank = when (index) {
                    0 -> messageManager.getMessage("result.stats.rank.first")
                    1 -> messageManager.getMessage("result.stats.rank.second")
                    2 -> messageManager.getMessage("result.stats.rank.third")
                    else -> messageManager.getMessage("result.stats.rank.other", mapOf("rank" to (index + 1)))
                }
                val message = messageManager.getMessage("result.stats.hunter-format", mapOf(
                    "rank" to rank,
                    "name" to stats.playerName,
                    "kills" to stats.kills,
                    "damage" to stats.damageDealt.toInt(),
                    "currency" to stats.earnedCurrency
                ))
                Bukkit.broadcastMessage(message)
            }
            Bukkit.broadcastMessage("")
        }
        
        // ランナー統計
        if (runnerStats.isNotEmpty()) {
            Bukkit.broadcastMessage(messageManager.getMessage("result.stats.runner-stats-header"))
            runnerStats.forEachIndexed { index, stats ->
                val rank = when (index) {
                    0 -> messageManager.getMessage("result.stats.rank.first")
                    1 -> messageManager.getMessage("result.stats.rank.second")
                    2 -> messageManager.getMessage("result.stats.rank.third")
                    else -> messageManager.getMessage("result.stats.rank.other", mapOf("rank" to (index + 1)))
                }
                val survivalMinutes = (stats.survivalTime / 60000).toInt()
                val survivalSeconds = ((stats.survivalTime % 60000) / 1000).toInt()
                val survivalTime = "${survivalMinutes}:${String.format("%02d", survivalSeconds)}"
                val message = messageManager.getMessage("result.stats.runner-format", mapOf(
                    "rank" to rank,
                    "name" to stats.playerName,
                    "survival" to survivalTime,
                    "dimensions" to stats.dimensionsVisited.size,
                    "currency" to stats.earnedCurrency
                ))
                Bukkit.broadcastMessage(message)
            }
        }
    }
    
    /**
     * MVP発表
     */
    private fun showMVPAnnouncement(mvp: GameStats.PlayerStatistics?, winningTeam: PlayerRole?) {
        if (mvp == null) return
        
        val starSeparator = messageManager.getMessage("result.mvp.header.separator")
        val headerLength = messageManager.getMessage("result.mvp.header.length").toIntOrNull() ?: 30
        val separatorLine = starSeparator.repeat(headerLength)
        
        Bukkit.broadcastMessage("")
        Bukkit.broadcastMessage("§6§l$separatorLine")
        Bukkit.broadcastMessage(messageManager.getMessage("result.mvp.header.title"))
        Bukkit.broadcastMessage("§6§l$separatorLine")
        
        val roleColor = messageManager.getMessage("result.roles.${mvp.role.name.lowercase()}.color")
        val roleIcon = messageManager.getMessage("result.roles.${mvp.role.name.lowercase()}.icon")
        
        val announcement = messageManager.getMessage("result.mvp.announcement", mapOf(
            "color" to roleColor,
            "icon" to roleIcon,
            "name" to mvp.playerName
        ))
        Bukkit.broadcastMessage(announcement)
        
        // MVP理由の表示
        when (mvp.role) {
            PlayerRole.HUNTER -> {
                val performance = messageManager.getMessage("result.mvp.hunter-performance", mapOf(
                    "kills" to mvp.kills,
                    "damage" to mvp.damageDealt.toInt()
                ))
                Bukkit.broadcastMessage(performance)
            }
            PlayerRole.RUNNER -> {
                val survivalMinutes = (mvp.survivalTime / 60000).toInt()
                val survivalSeconds = ((mvp.survivalTime % 60000) / 1000).toInt()
                val survivalTime = "${survivalMinutes}:${String.format("%02d", survivalSeconds)}"
                val performance = messageManager.getMessage("result.mvp.runner-performance", mapOf(
                    "survival" to survivalTime,
                    "dimensions" to mvp.dimensionsVisited.size
                ))
                Bukkit.broadcastMessage(performance)
            }
            else -> {}
        }
        
        Bukkit.broadcastMessage("§6§l$starSeparator")
        Bukkit.broadcastMessage("")
        
        // MVP専用効果
        Bukkit.getOnlinePlayers().find { it.uniqueId == mvp.playerId }?.let { player ->
            val mvpTitle = messageManager.getMessage(player, "result.mvp.title")
            val mvpSubtitle = messageManager.getMessage(player, "result.mvp.subtitle")
            player.sendTitle(mvpTitle, mvpSubtitle, 10, 60, 10)
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f)
            
            // MVP専用花火
            spawnFirework(player.location, listOf(Color.YELLOW, Color.ORANGE))
        }
    }
    
    /**
     * 個人成績表示
     */
    private fun showPersonalStats(gameStats: GameStats) {
        Bukkit.broadcastMessage(messageManager.getMessage("result.personal.header"))
        
        gameStats.getAllStats().values.sortedByDescending { 
            when (it.role) {
                PlayerRole.HUNTER -> it.kills * 100.0 + it.damageDealt
                PlayerRole.RUNNER -> it.survivalTime.toDouble() + (it.dimensionsVisited.size * 30000.0)
                else -> 0.0
            }
        }.forEach { stats ->
            val roleColor = messageManager.getMessage("result.roles.${stats.role.name.lowercase()}.color")
            val roleIcon = messageManager.getMessage("result.roles.${stats.role.name.lowercase()}.icon")
            
            val playerHeader = messageManager.getMessage("result.personal.player-format", mapOf(
                "color" to roleColor,
                "icon" to roleIcon,
                "name" to stats.playerName
            ))
            Bukkit.broadcastMessage(playerHeader)
            
            when (stats.role) {
                PlayerRole.HUNTER -> {
                    val kd = if (stats.deaths > 0) String.format("%.2f", stats.kills.toDouble() / stats.deaths) else messageManager.getMessage("result.personal.kd-infinity")
                    
                    val killDeathStats = messageManager.getMessage("result.personal.hunter.kill-death", mapOf(
                        "kills" to stats.kills,
                        "deaths" to stats.deaths,
                        "kd" to kd
                    ))
                    val damageStats = messageManager.getMessage("result.personal.hunter.damage", mapOf(
                        "dealt" to stats.damageDealt.toInt(),
                        "taken" to stats.damageTaken.toInt()
                    ))
                    val currencyStats = messageManager.getMessage("result.personal.hunter.currency", mapOf(
                        "earned" to stats.earnedCurrency,
                        "spent" to stats.spentCurrency,
                        "items" to stats.itemsPurchased
                    ))
                    
                    Bukkit.broadcastMessage(killDeathStats)
                    Bukkit.broadcastMessage(damageStats)
                    Bukkit.broadcastMessage(currencyStats)
                }
                PlayerRole.RUNNER -> {
                    val survivalMinutes = (stats.survivalTime / 60000).toInt()
                    val survivalSeconds = ((stats.survivalTime % 60000) / 1000).toInt()
                    val survivalTime = "${survivalMinutes}:${String.format("%02d", survivalSeconds)}"
                    
                    val survivalStats = messageManager.getMessage("result.personal.runner.survival", mapOf(
                        "survival" to survivalTime,
                        "deaths" to stats.deaths
                    ))
                    val explorationStats = messageManager.getMessage("result.personal.runner.exploration", mapOf(
                        "dimensions" to stats.dimensionsVisited.size,
                        "diamonds" to stats.diamondsCollected,
                        "escapes" to stats.escapeSuccesses
                    ))
                    val currencyStats = messageManager.getMessage("result.personal.runner.currency", mapOf(
                        "earned" to stats.earnedCurrency,
                        "spent" to stats.spentCurrency,
                        "items" to stats.itemsPurchased
                    ))
                    
                    Bukkit.broadcastMessage(survivalStats)
                    Bukkit.broadcastMessage(explorationStats)
                    Bukkit.broadcastMessage(currencyStats)
                }
                PlayerRole.SPECTATOR -> {
                    Bukkit.broadcastMessage(messageManager.getMessage("result.personal.spectator-stats"))
                }
            }
            Bukkit.broadcastMessage("")
        }
    }
    
    /**
     * 勝利演出開始
     */
    private fun startVictoryEffects(winningTeam: PlayerRole?) {
        val fireworkColors = when (winningTeam) {
            PlayerRole.HUNTER -> listOf(Color.RED, Color.MAROON, Color.ORANGE)
            PlayerRole.RUNNER -> listOf(Color.GREEN, Color.LIME, Color.YELLOW)
            else -> listOf(Color.BLUE, Color.PURPLE, Color.WHITE)
        }
        
        // 花火の連続打ち上げ
        fireworkTask = object : BukkitRunnable() {
            override fun run() {
                Bukkit.getOnlinePlayers().forEach { player ->
                    // プレイヤーの周囲に花火を打ち上げ
                    val loc = player.location.clone()
                    loc.add((Math.random() - 0.5) * 20, 10 + Math.random() * 10, (Math.random() - 0.5) * 20)
                    spawnFirework(loc, fireworkColors)
                }
            }
        }.runTaskTimer(plugin, 0L, 20L) // 1秒間隔
    }
    
    /**
     * 花火演出停止
     */
    private fun stopVictoryEffects() {
        fireworkTask?.cancel()
        fireworkTask = null
    }
    
    /**
     * 花火を打ち上げ
     */
    private fun spawnFirework(location: Location, colors: List<Color>) {
        val firework = location.world?.spawnEntity(location, EntityType.FIREWORK_ROCKET) as? Firework ?: return
        val meta = firework.fireworkMeta
        
        val effect = FireworkEffect.builder()
            .withColor(colors)
            .with(FireworkEffect.Type.BALL_LARGE)
            .flicker(true)
            .trail(true)
            .build()
        
        meta.addEffect(effect)
        meta.power = 1 + (Math.random() * 2).toInt()
        
        firework.fireworkMeta = meta
    }
    
    /**
     * リソースクリーンアップ
     */
    fun cleanup() {
        stopVictoryEffects()
    }
}