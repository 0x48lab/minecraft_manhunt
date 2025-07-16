package com.hacklab.manhunt.economy

import com.hacklab.manhunt.Main
import com.hacklab.manhunt.MessageManager
import com.hacklab.manhunt.PlayerRole
import org.bukkit.entity.Player
import java.util.*

/**
 * ゲーム内通貨システムの管理クラス
 */
class EconomyManager(private val plugin: Main) {
    private val playerBalances = mutableMapOf<UUID, Int>()
    private val earnHistory = mutableMapOf<UUID, MutableList<EarnRecord>>()
    private val messageManager: MessageManager
        get() = plugin.getMessageManager()
    
    init {
        // EarnReasonクラスでMessageManagerにアクセスできるよう設定
        EarnReason.setMessageManagerGetter { plugin.getMessageManager() }
    }
    
    fun getCurrencyUnit(): String {
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
        require(amount >= 0) { messageManager.getMessage(player, "economy.validation.balance-non-negative") }
        playerBalances[player.uniqueId] = amount
    }
    
    /**
     * プレイヤーに通貨を追加
     */
    fun addMoney(player: Player, amount: Int, reason: EarnReason) {
        require(amount >= 0) { messageManager.getMessage(player, "economy.validation.amount-non-negative") }
        
        val currentBalance = getBalance(player)
        val newBalance = currentBalance + amount
        playerBalances[player.uniqueId] = newBalance
        
        // ゲーム統計に通貨獲得を記録
        try {
            plugin.getGameManager().recordEarnedCurrency(player, amount)
        } catch (e: Exception) {
            plugin.logger.warning("Error recording earned currency statistics: ${e.message}")
        }
        
        // 獲得履歴を記録
        val history = earnHistory.getOrPut(player.uniqueId) { mutableListOf() }
        history.add(EarnRecord(reason, amount, System.currentTimeMillis()))
        
        // 通知（時間ボーナス系とダメージ報酬は表示を抑制）
        if (amount > 0) {
            val shouldShowMessage = when (reason) {
                is EarnReason.Hunter.TimeBonus -> false
                is EarnReason.Hunter.DamageDealt -> false  // ダメージ報酬も抑制
                is EarnReason.Hunter.TrackingPersistence -> true  // 追跡持続ボーナスは表示
                is EarnReason.Runner.SurvivalBonus -> false
                is EarnReason.Movement.Sprint -> false  // スプリント報酬も抑制（ログが多すぎるため）
                is EarnReason.AdminGrant -> false  // 管理者付与も抑制（別途通知）
                else -> true
            }
            
            if (shouldShowMessage) {
                val unit = getCurrencyUnit()
                val message = when (reason) {
                    is EarnReason.Hunter -> messageManager.getMessage(player, "economy.currency.hunter-earned",
                        "amount" to amount,
                        "unit" to unit,
                        "reason" to reason.getDescription(player)
                    )
                    is EarnReason.Runner -> messageManager.getMessage(player, "economy.currency.runner-earned",
                        "amount" to amount,
                        "unit" to unit,
                        "reason" to reason.getDescription(player)
                    )
                    is EarnReason.Movement -> messageManager.getMessage(player, "economy.currency.movement-earned",
                        "amount" to amount,
                        "unit" to unit,
                        "reason" to reason.getDescription(player)
                    )
                    is EarnReason.Advancement -> messageManager.getMessage(player, "economy.currency.advancement-earned",
                        "amount" to amount,
                        "unit" to unit,
                        "reason" to reason.getDescription(player)
                    )
                    is EarnReason.AdminGrant -> "" // 空文字（表示されない）
                }
                player.sendMessage(message)
            }
        }
        
        // ログも時間ボーナス系とダメージ報酬は抑制
        val shouldLog = when (reason) {
            is EarnReason.Hunter.TimeBonus -> false
            is EarnReason.Hunter.DamageDealt -> false  // ダメージ報酬も抑制
            is EarnReason.Hunter.TrackingPersistence -> true  // 追跡持続ボーナスはログに記録
            is EarnReason.Runner.SurvivalBonus -> false
            is EarnReason.Movement.Sprint -> false  // スプリント報酬も抑制
            is EarnReason.AdminGrant -> true  // 管理者付与はログに記録
            else -> true
        }
        
        if (shouldLog) {
            val unit = getCurrencyUnit()
            plugin.logger.info("${player.name} earned ${amount}${unit} (reason: ${reason.getDescription(null)}, balance: $newBalance${unit})")
        }
    }
    
    /**
     * プレイヤーから通貨を差し引く
     */
    fun removeMoney(player: Player, amount: Int): Boolean {
        require(amount >= 0) { messageManager.getMessage(player, "economy.validation.amount-non-negative") }
        
        val currentBalance = getBalance(player)
        if (currentBalance < amount) {
            return false
        }
        
        playerBalances[player.uniqueId] = currentBalance - amount
        
        // ゲーム統計に通貨消費を記録
        try {
            plugin.getGameManager().recordSpentCurrency(player, amount)
        } catch (e: Exception) {
            plugin.logger.warning("Error recording spent currency statistics: ${e.message}")
        }
        
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
        
        // 開始時の所持金を設定
        val startingBalance = plugin.getConfigManager().getCurrencyConfig().startingBalance
        val gameManager = plugin.getGameManager()
        
        // ゲームに参加しているプレイヤー全員に初期残高を設定
        for (player in plugin.server.onlinePlayers) {
            val role = gameManager.getPlayerRole(player)
            if (role != null && role != PlayerRole.SPECTATOR) {
                playerBalances[player.uniqueId] = startingBalance
            }
        }
        
        plugin.logger.info("Reset all player balances and set starting balance: $startingBalance")
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
    abstract fun getDescription(player: Player?): String
    
    companion object {
        private var messageManagerGetter: (() -> MessageManager)? = null
        
        fun setMessageManagerGetter(getter: () -> MessageManager) {
            messageManagerGetter = getter
        }
        
        protected fun getMessage(player: Player?, key: String, vararg placeholders: Pair<String, Any>): String {
            return messageManagerGetter?.invoke()?.getMessage(player, key, *placeholders) ?: key
        }
    }
    
    // ハンター用の獲得理由
    sealed class Hunter : EarnReason() {
        data class DamageDealt(val damage: Int) : Hunter() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.hunter.damage-dealt", "damage" to damage)
        }
        
        data class Kill(val runnerName: String) : Hunter() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.hunter.kill", "runner" to runnerName)
        }
        
        data class Proximity(val distance: Int) : Hunter() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.hunter.proximity", "distance" to distance)
        }
        
        object TimeBonus : Hunter() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.hunter.time-bonus")
        }
        
        data class TrackingPersistence(val runnerName: String, val duration: Int) : Hunter() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.hunter.tracking-persistence", 
                    "runner" to runnerName, 
                    "duration" to duration
                )
        }
    }
    
    // ランナー用の獲得理由
    sealed class Runner : EarnReason() {
        object SurvivalBonus : Runner() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.runner.survival-bonus")
        }
        
        data class Progress(val type: ProgressType) : Runner() {
            override fun getDescription(player: Player?) = when (type) {
                ProgressType.NETHER_ENTER -> getMessage(player, "earn-reasons.runner.nether-enter")
                ProgressType.FORTRESS_FOUND -> getMessage(player, "earn-reasons.runner.fortress-found")
                ProgressType.END_ENTER -> getMessage(player, "earn-reasons.runner.end-enter")
            }
        }
        
        data class ItemCollected(val itemName: String, val count: Int) : Runner() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.runner.item-collected", "item" to itemName, "count" to count)
        }
        
        object EscapeBonus : Runner() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.runner.escape-bonus")
        }
    }
    
    // 移動報酬
    sealed class Movement : EarnReason() {
        data class Sprint(val distance: Int) : Movement() {
            override fun getDescription(player: Player?) = 
                getMessage(player, "earn-reasons.movement.sprint", "distance" to distance)
        }
    }
    
    // 管理者からの付与
    data class AdminGrant(val adminName: String) : EarnReason() {
        override fun getDescription(player: Player?) = 
            getMessage(player, "earn-reasons.admin-grant", "admin" to adminName)
    }

    // 実績解除
    data class Advancement(val advancementName: String) : EarnReason() {
        override fun getDescription(player: Player?) = 
            getMessage(player, "earn-reasons.advancement", "advancement" to advancementName)
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