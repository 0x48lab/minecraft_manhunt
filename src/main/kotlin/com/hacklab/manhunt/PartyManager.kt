package com.hacklab.manhunt

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.ConcurrentHashMap

/**
 * パーティーシステムを管理するクラス
 */
class PartyManager(private val plugin: Main, private val gameManager: GameManager) {
    
    // パーティー情報を保存
    private val parties = ConcurrentHashMap<String, Party>()
    
    // プレイヤー名 -> パーティーIDのマッピング
    private val playerToParty = ConcurrentHashMap<String, String>()
    
    // 招待情報を保存
    private val invites = ConcurrentHashMap<String, PartyInvite>()
    
    // プレイヤー名 -> 招待IDのマッピング（受信した招待）
    private val playerToInvite = ConcurrentHashMap<String, String>()
    
    companion object {
        const val INVITE_TIMEOUT_MS = 60000L // 招待の有効期限（60秒）
    }
    
    init {
        // 定期的に期限切れの招待をクリーンアップ
        startInviteCleanupTask()
    }
    
    /**
     * パーティーを作成
     * @param leader パーティーリーダー
     * @return 作成されたパーティー、失敗時はnull
     */
    fun createParty(leader: Player): Party? {
        val leaderName = leader.name
        
        // 既にパーティーに参加している場合は作成不可
        if (getPlayerParty(leaderName) != null) {
            return null
        }
        
        // ゲーム中の場合、現在の役割を取得
        val currentRole = gameManager.getPlayerRole(leader)
        
        val party = Party(
            leaderName = leaderName,
            role = currentRole
        )
        
        parties[party.id] = party
        playerToParty[leaderName] = party.id
        
        plugin.logger.info("パーティー作成: ${party.id} by $leaderName")
        return party
    }
    
    /**
     * 招待を送信
     * @param inviter 招待者
     * @param inviteeName 被招待者名
     * @return 送信成功時true
     */
    fun sendInvite(inviter: Player, inviteeName: String): Boolean {
        val inviterName = inviter.name
        val party = getPlayerParty(inviterName) ?: return false
        
        // リーダーのみが招待可能
        if (!party.isLeader(inviterName)) {
            return false
        }
        
        // パーティーが満員の場合は招待不可
        if (party.isFull()) {
            return false
        }
        
        // 被招待者がオンラインかチェック
        val invitee = plugin.server.getPlayer(inviteeName) ?: return false
        
        // 被招待者が既にパーティーに参加している場合は招待不可
        if (getPlayerParty(inviteeName) != null) {
            return false
        }
        
        // 役割が異なる場合は招待不可（ゲーム中）
        if (gameManager.getGameState() == GameState.RUNNING) {
            val inviterRole = gameManager.getPlayerRole(inviter)
            val inviteeRole = gameManager.getPlayerRole(invitee)
            
            if (inviterRole != inviteeRole) {
                return false
            }
        }
        
        // 既存の招待をキャンセル
        cancelPendingInvite(inviteeName)
        
        // 新しい招待を作成
        val invite = PartyInvite(
            partyId = party.id,
            inviterName = inviterName,
            inviteeName = inviteeName
        )
        
        invites[invite.id] = invite
        playerToInvite[inviteeName] = invite.id
        
        // 招待通知を送信
        sendInviteNotification(inviter, invitee, party)
        
        plugin.logger.info("パーティー招待送信: $inviterName -> $inviteeName (パーティー: ${party.id})")
        return true
    }
    
    /**
     * 招待を承諾
     * @param invitee 被招待者
     * @return 承諾成功時true
     */
    fun acceptInvite(invitee: Player): Boolean {
        val inviteeName = invitee.name
        val inviteId = playerToInvite[inviteeName] ?: return false
        val invite = invites[inviteId] ?: return false
        
        // 招待が期限切れまたは承諾済みの場合
        if (invite.status != InviteStatus.PENDING || invite.isExpired(INVITE_TIMEOUT_MS)) {
            cleanupInvite(inviteId)
            return false
        }
        
        val party = parties[invite.partyId] ?: return false
        
        // パーティーが満員または非アクティブの場合
        if (party.isFull() || !party.isActive()) {
            cleanupInvite(inviteId)
            return false
        }
        
        // パーティーに参加
        if (party.addMember(inviteeName)) {
            playerToParty[inviteeName] = party.id
            invite.status = InviteStatus.ACCEPTED
            
            // 参加通知を送信
            sendJoinNotification(invitee, party)
            
            // 招待をクリーンアップ
            cleanupInvite(inviteId)
            
            plugin.logger.info("パーティー参加: $inviteeName -> パーティー ${party.id}")
            return true
        }
        
        return false
    }
    
    /**
     * 招待を拒否
     * @param invitee 被招待者
     * @return 拒否成功時true
     */
    fun declineInvite(invitee: Player): Boolean {
        val inviteeName = invitee.name
        val inviteId = playerToInvite[inviteeName] ?: return false
        val invite = invites[inviteId] ?: return false
        
        invite.status = InviteStatus.DECLINED
        
        // 拒否通知を送信
        val party = parties[invite.partyId]
        party?.let { sendDeclineNotification(invitee, it) }
        
        // 招待をクリーンアップ
        cleanupInvite(inviteId)
        
        plugin.logger.info("パーティー招待拒否: $inviteeName")
        return true
    }
    
    /**
     * パーティーから脱退
     * @param player 脱退するプレイヤー
     * @return 脱退成功時true
     */
    fun leaveParty(player: Player): Boolean {
        val playerName = player.name
        val party = getPlayerParty(playerName) ?: return false
        
        // リーダーの場合はパーティー解散
        if (party.isLeader(playerName)) {
            return disbandParty(party.id)
        }
        
        // メンバーの場合は脱退
        if (party.removeMember(playerName)) {
            playerToParty.remove(playerName)
            
            // 脱退通知を送信
            sendLeaveNotification(player, party)
            
            plugin.logger.info("パーティー脱退: $playerName from パーティー ${party.id}")
            return true
        }
        
        return false
    }
    
    /**
     * メンバーを除名
     * @param kicker 除名実行者
     * @param targetName 除名対象者名
     * @return 除名成功時true
     */
    fun kickMember(kicker: Player, targetName: String): Boolean {
        val kickerName = kicker.name
        val party = getPlayerParty(kickerName) ?: return false
        
        // リーダーのみが除名可能
        if (!party.isLeader(kickerName)) {
            return false
        }
        
        // 対象がメンバーでない場合
        if (!party.isMember(targetName)) {
            return false
        }
        
        // リーダーは除名不可
        if (party.isLeader(targetName)) {
            return false
        }
        
        if (party.removeMember(targetName)) {
            playerToParty.remove(targetName)
            
            // 除名通知を送信
            sendKickNotification(kicker, targetName, party)
            
            plugin.logger.info("パーティーメンバー除名: $kickerName kicked $targetName from パーティー ${party.id}")
            return true
        }
        
        return false
    }
    
    /**
     * パーティーを解散
     * @param partyId パーティーID
     * @return 解散成功時true
     */
    fun disbandParty(partyId: String): Boolean {
        val party = parties[partyId] ?: return false
        
        // 解散通知を送信
        sendDisbandNotification(party)
        
        // メンバーのマッピングを削除
        party.members.forEach { memberName ->
            playerToParty.remove(memberName)
        }
        
        // パーティーを解散
        party.disband()
        parties.remove(partyId)
        
        plugin.logger.info("パーティー解散: $partyId")
        return true
    }
    
    /**
     * プレイヤーのパーティーを取得
     * @param playerName プレイヤー名
     * @return パーティー、参加していない場合はnull
     */
    fun getPlayerParty(playerName: String): Party? {
        val partyId = playerToParty[playerName] ?: return null
        return parties[partyId]
    }
    
    /**
     * プレイヤーが受信中の招待を取得
     * @param playerName プレイヤー名
     * @return 招待、ない場合はnull
     */
    fun getPendingInvite(playerName: String): PartyInvite? {
        val inviteId = playerToInvite[playerName] ?: return null
        val invite = invites[inviteId] ?: return null
        
        if (invite.status == InviteStatus.PENDING && !invite.isExpired(INVITE_TIMEOUT_MS)) {
            return invite
        }
        
        // 期限切れまたは無効な招待をクリーンアップ
        cleanupInvite(inviteId)
        return null
    }
    
    /**
     * 全パーティーリストを取得
     */
    fun getAllParties(): List<Party> {
        return parties.values.filter { it.isActive() }
    }
    
    /**
     * プレイヤーのログアウト処理
     * @param playerName ログアウトしたプレイヤー名
     */
    fun handlePlayerLogout(playerName: String) {
        // 受信中の招待をキャンセル
        cancelPendingInvite(playerName)
        
        // ゲーム中でない場合、パーティーから脱退
        if (gameManager.getGameState() != GameState.RUNNING) {
            val party = getPlayerParty(playerName)
            if (party != null) {
                if (party.isLeader(playerName)) {
                    disbandParty(party.id)
                } else {
                    party.removeMember(playerName)
                    playerToParty.remove(playerName)
                }
            }
        }
    }
    
    // 以下、プライベートメソッド
    
    private fun cancelPendingInvite(playerName: String) {
        val inviteId = playerToInvite[playerName]
        if (inviteId != null) {
            cleanupInvite(inviteId)
        }
    }
    
    private fun cleanupInvite(inviteId: String) {
        val invite = invites.remove(inviteId)
        invite?.let {
            playerToInvite.remove(it.inviteeName)
        }
    }
    
    private fun startInviteCleanupTask() {
        object : BukkitRunnable() {
            override fun run() {
                val expiredInvites = invites.values.filter { 
                    it.isExpired(INVITE_TIMEOUT_MS) 
                }
                
                expiredInvites.forEach { invite ->
                    invite.status = InviteStatus.EXPIRED
                    cleanupInvite(invite.id)
                }
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30) // 30秒ごとに実行
    }
    
    // 通知メソッド群
    private fun sendInviteNotification(inviter: Player, invitee: Player, party: Party) {
        val roleText = party.role?.let { " (${it.name})" } ?: ""
        
        inviter.sendMessage("§a[パーティー] §f${invitee.name} §aに招待を送信しました$roleText")
        invitee.sendMessage("§6[パーティー招待] §f${inviter.name} §6からパーティー$roleText に招待されました")
        invitee.sendMessage("§e/manhunt party accept §7- 承諾")
        invitee.sendMessage("§e/manhunt party decline §7- 拒否")
        invitee.sendMessage("§c招待は60秒で期限切れになります")
    }
    
    private fun sendJoinNotification(player: Player, party: Party) {
        val roleText = party.role?.let { " (${it.name})" } ?: ""
        
        // 参加者への通知
        player.sendMessage("§a[パーティー] パーティー$roleText に参加しました！")
        
        // 既存メンバーへの通知
        party.getOnlineMembers().forEach { member ->
            if (member.name != player.name) {
                member.sendMessage("§a[パーティー] §f${player.name} §aがパーティーに参加しました")
            }
        }
    }
    
    private fun sendLeaveNotification(player: Player, party: Party) {
        val roleText = party.role?.let { " (${it.name})" } ?: ""
        
        // 脱退者への通知
        player.sendMessage("§c[パーティー] パーティー$roleText から脱退しました")
        
        // 残りメンバーへの通知
        party.getOnlineMembers().forEach { member ->
            if (member.name != player.name) {
                member.sendMessage("§c[パーティー] §f${player.name} §cがパーティーから脱退しました")
            }
        }
    }
    
    private fun sendKickNotification(kicker: Player, targetName: String, party: Party) {
        val roleText = party.role?.let { " (${it.name})" } ?: ""
        val target = plugin.server.getPlayer(targetName)
        
        // 除名された人への通知
        target?.sendMessage("§c[パーティー] パーティー$roleText から除名されました")
        
        // 除名実行者への通知
        kicker.sendMessage("§c[パーティー] §f$targetName §cを除名しました")
        
        // 他のメンバーへの通知
        party.getOnlineMembers().forEach { member ->
            if (member.name != kicker.name && member.name != targetName) {
                member.sendMessage("§c[パーティー] §f$targetName §cが除名されました")
            }
        }
    }
    
    private fun sendDeclineNotification(player: Player, party: Party) {
        val roleText = party.role?.let { " (${it.name})" } ?: ""
        
        // 拒否者への通知
        player.sendMessage("§c[パーティー] 招待を拒否しました")
        
        // リーダーへの通知
        val leader = plugin.server.getPlayer(party.leaderName)
        leader?.sendMessage("§c[パーティー] §f${player.name} §cが招待を拒否しました")
    }
    
    private fun sendDisbandNotification(party: Party) {
        val roleText = party.role?.let { " (${it.name})" } ?: ""
        
        party.getOnlineMembers().forEach { member ->
            member.sendMessage("§c[パーティー] パーティー$roleText が解散されました")
        }
    }
    
    /**
     * クリーンアップ処理
     */
    fun cleanup() {
        parties.clear()
        playerToParty.clear()
        invites.clear()
        playerToInvite.clear()
    }
}