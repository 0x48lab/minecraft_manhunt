package com.hacklab.manhunt

import org.bukkit.entity.Player
import java.util.*

/**
 * バディー（相棒）システム管理クラス
 * 仲間同士で1人だけ相棒を設定でき、相互承認が必要
 */
class BuddySystem(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val messageManager: MessageManager
) {
    // プレイヤー -> 現在のバディー
    private val buddyPairs = mutableMapOf<UUID, UUID>()
    
    // プレイヤー -> 送信した招待のターゲット
    private val pendingInvites = mutableMapOf<UUID, UUID>()
    
    /**
     * バディー招待を送信
     */
    fun sendBuddyInvite(sender: Player, target: Player): Boolean {
        // ゲーム状態チェック
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.only-during-game"))
            return false
        }
        
        // 同じチームかチェック
        val senderRole = gameManager.getPlayerRole(sender)
        val targetRole = gameManager.getPlayerRole(target)
        
        if (senderRole == null || targetRole == null || senderRole != targetRole || 
            senderRole == PlayerRole.SPECTATOR) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.only-teammates"))
            return false
        }
        
        // 自分自身は指定不可
        if (sender == target) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.cannot-self"))
            return false
        }
        
        // 既にバディーがいる場合
        if (buddyPairs.containsKey(sender.uniqueId)) {
            val currentBuddy = plugin.server.getPlayer(buddyPairs[sender.uniqueId]!!)
            sender.sendMessage(messageManager.getMessage(sender, "buddy.already-have-buddy", 
                mapOf("buddy" to (currentBuddy?.name ?: "Unknown"))))
            return false
        }
        
        // 相手が既にバディーを持っている場合
        if (buddyPairs.containsKey(target.uniqueId)) {
            val targetBuddy = plugin.server.getPlayer(buddyPairs[target.uniqueId]!!)
            sender.sendMessage(messageManager.getMessage(sender, "buddy.target-has-buddy", 
                mapOf("player" to target.name, "buddy" to (targetBuddy?.name ?: "Unknown"))))
            return false
        }
        
        // 既に招待を送信済みの場合
        if (pendingInvites[sender.uniqueId] == target.uniqueId) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.invite-already-sent", 
                mapOf("player" to target.name)))
            return false
        }
        
        // 招待を記録
        pendingInvites[sender.uniqueId] = target.uniqueId
        
        // メッセージ送信
        sender.sendMessage(messageManager.getMessage(sender, "buddy.invite-sent", 
            mapOf("player" to target.name)))
        target.sendMessage(messageManager.getMessage(target, "buddy.invite-received", 
            mapOf("sender" to sender.name)))
        target.sendMessage(messageManager.getMessage(target, "buddy.invite-instructions"))
        
        return true
    }
    
    /**
     * バディー招待を承認
     */
    fun acceptBuddyInvite(accepter: Player, sender: Player): Boolean {
        // 招待が存在するかチェック
        if (pendingInvites[sender.uniqueId] != accepter.uniqueId) {
            accepter.sendMessage(messageManager.getMessage(accepter, "buddy.no-invite-from", 
                mapOf("player" to sender.name)))
            return false
        }
        
        // 両方がバディーを持っていないことを再確認
        if (buddyPairs.containsKey(sender.uniqueId) || buddyPairs.containsKey(accepter.uniqueId)) {
            accepter.sendMessage(messageManager.getMessage(accepter, "buddy.someone-has-buddy"))
            pendingInvites.remove(sender.uniqueId)
            return false
        }
        
        // バディー関係を確立
        buddyPairs[sender.uniqueId] = accepter.uniqueId
        buddyPairs[accepter.uniqueId] = sender.uniqueId
        
        // 招待を削除
        pendingInvites.remove(sender.uniqueId)
        
        // 成功メッセージ
        sender.sendMessage(messageManager.getMessage(sender, "buddy.paired-success", 
            mapOf("buddy" to accepter.name)))
        accepter.sendMessage(messageManager.getMessage(accepter, "buddy.paired-success", 
            mapOf("buddy" to sender.name)))
        
        return true
    }
    
    /**
     * バディー関係を解除
     */
    fun removeBuddy(player: Player): Boolean {
        val buddyId = buddyPairs[player.uniqueId]
        if (buddyId == null) {
            player.sendMessage(messageManager.getMessage(player, "buddy.no-buddy"))
            return false
        }
        
        val buddy = plugin.server.getPlayer(buddyId)
        
        // 両方向の関係を削除
        buddyPairs.remove(player.uniqueId)
        buddyPairs.remove(buddyId)
        
        // メッセージ送信
        player.sendMessage(messageManager.getMessage(player, "buddy.removed", 
            mapOf("buddy" to (buddy?.name ?: "Unknown"))))
        buddy?.sendMessage(messageManager.getMessage(buddy, "buddy.partner-removed", 
            mapOf("partner" to player.name)))
        
        return true
    }
    
    /**
     * 招待を拒否
     */
    fun declineBuddyInvite(decliner: Player, sender: Player): Boolean {
        if (pendingInvites[sender.uniqueId] != decliner.uniqueId) {
            decliner.sendMessage(messageManager.getMessage(decliner, "buddy.no-invite-from", 
                mapOf("player" to sender.name)))
            return false
        }
        
        pendingInvites.remove(sender.uniqueId)
        
        sender.sendMessage(messageManager.getMessage(sender, "buddy.invite-declined", 
            mapOf("player" to decliner.name)))
        decliner.sendMessage(messageManager.getMessage(decliner, "buddy.invite-declined-by-you", 
            mapOf("player" to sender.name)))
        
        return true
    }
    
    /**
     * プレイヤーのバディーを取得
     */
    fun getBuddy(player: Player): Player? {
        val buddyId = buddyPairs[player.uniqueId] ?: return null
        return plugin.server.getPlayer(buddyId)
    }
    
    /**
     * プレイヤーが送信中の招待があるかチェック
     */
    fun hasPendingInvite(player: Player): Player? {
        val targetId = pendingInvites[player.uniqueId] ?: return null
        return plugin.server.getPlayer(targetId)
    }
    
    /**
     * プレイヤーが受信した招待があるかチェック
     */
    fun getIncomingInvites(player: Player): List<Player> {
        return pendingInvites.entries
            .filter { it.value == player.uniqueId }
            .mapNotNull { plugin.server.getPlayer(it.key) }
    }
    
    /**
     * バディーとの相対座標を取得
     */
    fun getBuddyRelativeCoordinates(player: Player): String? {
        val buddy = getBuddy(player) ?: return null
        
        if (!buddy.isOnline || buddy.world != player.world) {
            return messageManager.getMessage(player, "buddy.not-in-same-world")
        }
        
        try {
            val deltaX = buddy.location.blockX - player.location.blockX
            val deltaY = buddy.location.blockY - player.location.blockY
            val deltaZ = buddy.location.blockZ - player.location.blockZ
            
            val xSign = if (deltaX >= 0) "+" else ""
            val ySign = if (deltaY >= 0) "+" else ""
            val zSign = if (deltaZ >= 0) "+" else ""
            
            return "X:$xSign$deltaX Y:$ySign$deltaY Z:$zSign$deltaZ"
        } catch (e: Exception) {
            return messageManager.getMessage(player, "buddy.coordinate-error")
        }
    }
    
    /**
     * ゲーム開始時の初期化
     */
    fun onGameStart() {
        buddyPairs.clear()
        pendingInvites.clear()
    }
    
    /**
     * ゲーム終了時のクリーンアップ
     */
    fun onGameEnd() {
        buddyPairs.clear()
        pendingInvites.clear()
    }
    
    /**
     * プレイヤー退出時の処理
     */
    fun onPlayerLeave(player: Player) {
        val buddyId = buddyPairs[player.uniqueId]
        if (buddyId != null) {
            // バディー関係を解除
            buddyPairs.remove(player.uniqueId)
            buddyPairs.remove(buddyId)
            
            // バディーに通知
            val buddy = plugin.server.getPlayer(buddyId)
            buddy?.sendMessage(messageManager.getMessage(buddy, "buddy.partner-left", 
                mapOf("partner" to player.name)))
        }
        
        // 送信した招待を削除
        pendingInvites.remove(player.uniqueId)
        
        // 受信した招待を削除
        pendingInvites.entries.removeIf { it.value == player.uniqueId }
    }
}