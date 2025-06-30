package com.hacklab.manhunt

import org.bukkit.entity.Player
import java.util.*
import kotlin.collections.HashMap

/**
 * ゲーム統計情報を管理するクラス
 */
class GameStats {
    
    // ゲーム全体の統計
    private var gameStartTime: Long = 0
    private var gameEndTime: Long = 0
    private var winningTeam: PlayerRole? = null
    private var winCondition: WinCondition? = null
    
    // プレイヤー個別統計
    private val playerStats = HashMap<UUID, PlayerStatistics>()
    
    // プレイヤー統計データクラス
    data class PlayerStatistics(
        val playerId: UUID,
        val playerName: String,
        val role: PlayerRole,
        var kills: Int = 0,
        var deaths: Int = 0,
        var damageDealt: Double = 0.0,
        var damageTaken: Double = 0.0,
        var survivalTime: Long = 0, // ミリ秒
        var earnedCurrency: Int = 0,
        var spentCurrency: Int = 0,
        var itemsPurchased: Int = 0,
        var dimensionsVisited: MutableSet<String> = mutableSetOf(),
        var diamondsCollected: Int = 0,
        var escapeSuccesses: Int = 0, // ランナー専用: ハンターから逃走成功回数
        var trackingAccuracy: Double = 0.0, // ハンター専用: 追跡精度
        var joinTime: Long = System.currentTimeMillis(),
        var leftTime: Long? = null
    )
    
    // 勝利条件
    enum class WinCondition {
        ENDER_DRAGON_KILLED,    // エンダードラゴン討伐
        ALL_RUNNERS_ELIMINATED, // 全ランナー撃破
        ALL_HUNTERS_LEFT,       // 全ハンター退出
        ALL_RUNNERS_LEFT        // 全ランナー退出
    }
    
    /**
     * ゲーム開始時の初期化
     */
    fun startGame() {
        gameStartTime = System.currentTimeMillis()
        gameEndTime = 0
        winningTeam = null
        winCondition = null
        playerStats.clear()
    }
    
    /**
     * ゲーム終了処理
     */
    fun endGame(winningTeam: PlayerRole, condition: WinCondition) {
        gameEndTime = System.currentTimeMillis()
        this.winningTeam = winningTeam
        this.winCondition = condition
        
        // 全プレイヤーの生存時間を更新
        updateAllSurvivalTimes()
    }
    
    /**
     * プレイヤーをゲームに追加
     */
    fun addPlayer(player: Player, role: PlayerRole) {
        val stats = PlayerStatistics(
            playerId = player.uniqueId,
            playerName = player.name,
            role = role,
            joinTime = System.currentTimeMillis()
        )
        playerStats[player.uniqueId] = stats
    }
    
    /**
     * プレイヤーの役割変更
     */
    fun updatePlayerRole(player: Player, newRole: PlayerRole) {
        playerStats[player.uniqueId]?.let { stats ->
            playerStats[player.uniqueId] = stats.copy(role = newRole)
        }
    }
    
    /**
     * プレイヤーの退出処理
     */
    fun playerLeft(player: Player) {
        playerStats[player.uniqueId]?.let { stats ->
            stats.leftTime = System.currentTimeMillis()
            stats.survivalTime = (stats.leftTime!! - stats.joinTime)
        }
    }
    
    /**
     * キル記録
     */
    fun addKill(killer: Player, victim: Player) {
        playerStats[killer.uniqueId]?.kills = (playerStats[killer.uniqueId]?.kills ?: 0) + 1
        playerStats[victim.uniqueId]?.deaths = (playerStats[victim.uniqueId]?.deaths ?: 0) + 1
    }
    
    /**
     * ダメージ記録
     */
    fun addDamage(attacker: Player, victim: Player, damage: Double) {
        playerStats[attacker.uniqueId]?.damageDealt = (playerStats[attacker.uniqueId]?.damageDealt ?: 0.0) + damage
        playerStats[victim.uniqueId]?.damageTaken = (playerStats[victim.uniqueId]?.damageTaken ?: 0.0) + damage
    }
    
    /**
     * 通貨獲得記録
     */
    fun addEarnedCurrency(player: Player, amount: Int) {
        playerStats[player.uniqueId]?.earnedCurrency = (playerStats[player.uniqueId]?.earnedCurrency ?: 0) + amount
    }
    
    /**
     * 通貨消費記録
     */
    fun addSpentCurrency(player: Player, amount: Int) {
        playerStats[player.uniqueId]?.spentCurrency = (playerStats[player.uniqueId]?.spentCurrency ?: 0) + amount
        playerStats[player.uniqueId]?.itemsPurchased = (playerStats[player.uniqueId]?.itemsPurchased ?: 0) + 1
    }
    
    /**
     * ディメンション訪問記録
     */
    fun addDimensionVisit(player: Player, worldName: String) {
        playerStats[player.uniqueId]?.dimensionsVisited?.add(worldName)
    }
    
    /**
     * ダイヤモンド収集記録
     */
    fun addDiamondCollected(player: Player, count: Int = 1) {
        playerStats[player.uniqueId]?.diamondsCollected = (playerStats[player.uniqueId]?.diamondsCollected ?: 0) + count
    }
    
    /**
     * 逃走成功記録（ランナー専用）
     */
    fun addEscapeSuccess(runner: Player) {
        playerStats[runner.uniqueId]?.escapeSuccesses = (playerStats[runner.uniqueId]?.escapeSuccesses ?: 0) + 1
    }
    
    /**
     * 全プレイヤーの生存時間を更新
     */
    private fun updateAllSurvivalTimes() {
        val currentTime = System.currentTimeMillis()
        playerStats.values.forEach { stats ->
            if (stats.leftTime == null) {
                stats.survivalTime = currentTime - stats.joinTime
            }
        }
    }
    
    /**
     * ゲーム時間を取得（分:秒形式）
     */
    fun getGameDuration(): String {
        val duration = if (gameEndTime > 0) {
            gameEndTime - gameStartTime
        } else {
            System.currentTimeMillis() - gameStartTime
        }
        
        val minutes = (duration / 60000).toInt()
        val seconds = ((duration % 60000) / 1000).toInt()
        return String.format("%d:%02d", minutes, seconds)
    }
    
    /**
     * MVP選出
     */
    fun getMVP(): PlayerStatistics? {
        if (playerStats.isEmpty()) return null
        
        return when (winningTeam) {
            PlayerRole.HUNTER -> {
                // ハンター勝利時: キル数 + ダメージ量で評価
                playerStats.values
                    .filter { it.role == PlayerRole.HUNTER }
                    .maxByOrNull { it.kills * 100 + it.damageDealt }
            }
            PlayerRole.RUNNER -> {
                // ランナー勝利時: 生存時間 + 貢献度で評価
                playerStats.values
                    .filter { it.role == PlayerRole.RUNNER }
                    .maxByOrNull { 
                        it.survivalTime + (it.dimensionsVisited.size * 30000) + (it.diamondsCollected * 5000)
                    }
            }
            else -> null
        }
    }
    
    /**
     * チーム統計を取得
     */
    fun getTeamStats(role: PlayerRole): List<PlayerStatistics> {
        return playerStats.values.filter { it.role == role }.sortedByDescending { 
            when (role) {
                PlayerRole.HUNTER -> it.kills * 100.0 + it.damageDealt
                PlayerRole.RUNNER -> it.survivalTime.toDouble() + (it.dimensionsVisited.size * 30000.0)
                else -> 0.0
            }
        }
    }
    
    /**
     * 全統計を取得
     */
    fun getAllStats(): Map<UUID, PlayerStatistics> = playerStats.toMap()
    
    /**
     * 勝利チームを取得
     */
    fun getWinningTeam(): PlayerRole? = winningTeam
    
    /**
     * 勝利条件を取得
     */
    fun getWinCondition(): WinCondition? = winCondition
    
    /**
     * プレイヤー統計を取得
     */
    fun getPlayerStats(playerId: UUID): PlayerStatistics? = playerStats[playerId]
    
    /**
     * ゲーム開始時刻を取得
     */
    fun getGameStartTime(): Long = gameStartTime
    
    /**
     * ゲーム終了時刻を取得
     */
    fun getGameEndTime(): Long = gameEndTime
}