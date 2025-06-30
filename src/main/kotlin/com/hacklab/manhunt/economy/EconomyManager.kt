package com.hacklab.manhunt.economy

import com.hacklab.manhunt.Main
import com.hacklab.manhunt.PlayerRole
import org.bukkit.entity.Player
import java.util.*

/**
 * ゲーム内通貨システムの管理クラス
 */
class EconomyManager(private val plugin: Main) {
    private val playerBalances = mutableMapOf<UUID, Int>()
    private val earnHistory = mutableMapOf<UUID, MutableList<EarnRecord>>()
    
    private fun getCurrencyUnit(): String {
        return plugin.getConfigManager().getCurrencyConfig().currencyUnit
    }
    
    /**
     * プレイヤーの残高を取得
     */
    fun getBalance(player: Player): Int {
        return playerBalances[player.uniqueId] ?: 0
    }
    
    /**
     * プレイヤーの残高を設定
     */
    fun setBalance(player: Player, amount: Int) {
        require(amount >= 0) { "残高は0以上である必要があります" }
        playerBalances[player.uniqueId] = amount
    }
    
    /**
     * プレイヤーに通貨を追加
     */
    fun addMoney(player: Player, amount: Int, reason: EarnReason) {
        require(amount >= 0) { "追加金額は0以上である必要があります" }
        
        val currentBalance = getBalance(player)
        val newBalance = currentBalance + amount
        playerBalances[player.uniqueId] = newBalance
        
        // 獲得履歴を記録
        val history = earnHistory.getOrPut(player.uniqueId) { mutableListOf() }
        history.add(EarnRecord(reason, amount, System.currentTimeMillis()))
        
        // 通知（時間ボーナス系は表示を抑制）
        if (amount > 0) {
            val shouldShowMessage = when (reason) {
                is EarnReason.Hunter.TimeBonus -> false
                is EarnReason.Runner.SurvivalBonus -> false
                else -> true
            }
            
            if (shouldShowMessage) {
                val unit = getCurrencyUnit()
                val message = when (reason) {
                    is EarnReason.Hunter -> "§c[+${amount}${unit}] ${reason.getDescription()}"
                    is EarnReason.Runner -> "§a[+${amount}${unit}] ${reason.getDescription()}"
                }
                player.sendMessage(message)
            }
        }
        
        // ログも時間ボーナス系は抑制
        val shouldLog = when (reason) {
            is EarnReason.Hunter.TimeBonus -> false
            is EarnReason.Runner.SurvivalBonus -> false
            else -> true
        }
        
        if (shouldLog) {
            val unit = getCurrencyUnit()
            plugin.logger.info("${player.name} が ${amount}${unit}獲得 (理由: ${reason.getDescription()}, 残高: $newBalance${unit})")
        }
    }
    
    /**
     * プレイヤーから通貨を差し引く
     */
    fun removeMoney(player: Player, amount: Int): Boolean {
        require(amount >= 0) { "差引金額は0以上である必要があります" }
        
        val currentBalance = getBalance(player)
        if (currentBalance < amount) {
            return false
        }
        
        playerBalances[player.uniqueId] = currentBalance - amount
        return true
    }
    
    /**
     * プレイヤーが指定金額を持っているか確認
     */
    fun hasEnoughMoney(player: Player, amount: Int): Boolean {
        return getBalance(player) >= amount
    }
    
    /**
     * ゲーム開始時のリセット
     */
    fun resetAllBalances() {
        playerBalances.clear()
        earnHistory.clear()
        plugin.logger.info("全プレイヤーの残高をリセットしました")
    }
    
    /**
     * プレイヤー個別のリセット
     */
    fun resetPlayerBalance(player: Player) {
        playerBalances.remove(player.uniqueId)
        earnHistory.remove(player.uniqueId)
    }
    
    /**
     * 獲得履歴の取得
     */
    fun getEarnHistory(player: Player): List<EarnRecord> {
        return earnHistory[player.uniqueId]?.toList() ?: emptyList()
    }
}

/**
 * 通貨獲得の理由を表す封印クラス
 */
sealed class EarnReason {
    abstract fun getDescription(): String
    
    // ハンター用の獲得理由
    sealed class Hunter : EarnReason() {
        data class DamageDealt(val damage: Int) : Hunter() {
            override fun getDescription() = "ランナーに${damage}ダメージを与えた"
        }
        
        data class Kill(val runnerName: String) : Hunter() {
            override fun getDescription() = "${runnerName}を倒した"
        }
        
        data class Proximity(val distance: Int) : Hunter() {
            override fun getDescription() = "ランナーとの距離${distance}m以内に接近"
        }
        
        object TimeBonus : Hunter() {
            override fun getDescription() = "追跡ボーナス"
        }
    }
    
    // ランナー用の獲得理由
    sealed class Runner : EarnReason() {
        object SurvivalBonus : Runner() {
            override fun getDescription() = "生存ボーナス"
        }
        
        data class Progress(val type: ProgressType) : Runner() {
            override fun getDescription() = when (type) {
                ProgressType.NETHER_ENTER -> "ネザーに到達"
                ProgressType.FORTRESS_FOUND -> "要塞を発見"
                ProgressType.END_ENTER -> "エンドに到達"
            }
        }
        
        data class ItemCollected(val itemName: String, val count: Int) : Runner() {
            override fun getDescription() = "${itemName}を${count}個収集"
        }
        
        object EscapeBonus : Runner() {
            override fun getDescription() = "逃走成功ボーナス"
        }
    }
}

/**
 * 進捗の種類
 */
enum class ProgressType {
    NETHER_ENTER,
    FORTRESS_FOUND,
    END_ENTER
}

/**
 * 獲得記録
 */
data class EarnRecord(
    val reason: EarnReason,
    val amount: Int,
    val timestamp: Long
)