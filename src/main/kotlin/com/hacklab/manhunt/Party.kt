package com.hacklab.manhunt

import org.bukkit.entity.Player
import java.util.*

/**
 * パーティーの状態を表す列挙型
 */
enum class PartyStatus {
    ACTIVE,    // アクティブなパーティー
    DISBANDED  // 解散済みパーティー
}

/**
 * パーティーの招待状態を表す列挙型
 */
enum class InviteStatus {
    PENDING,   // 招待中
    ACCEPTED,  // 承諾済み
    DECLINED,  // 拒否済み
    EXPIRED    // 期限切れ
}

/**
 * パーティー招待情報を管理するデータクラス
 */
data class PartyInvite(
    val id: String = UUID.randomUUID().toString(),
    val partyId: String,
    val inviterName: String,
    val inviteeName: String,
    val inviteTime: Long = System.currentTimeMillis(),
    var status: InviteStatus = InviteStatus.PENDING
) {
    /**
     * 招待が期限切れかどうかを確認
     * @param timeoutMs 招待の有効期限（ミリ秒）
     * @return 期限切れの場合true
     */
    fun isExpired(timeoutMs: Long = 60000L): Boolean {
        return System.currentTimeMillis() - inviteTime > timeoutMs
    }
}

/**
 * パーティー情報を管理するデータクラス
 */
data class Party(
    val id: String = UUID.randomUUID().toString(),
    val leaderName: String,
    val members: MutableList<String> = mutableListOf(),
    val createdTime: Long = System.currentTimeMillis(),
    var status: PartyStatus = PartyStatus.ACTIVE,
    var role: PlayerRole? = null
) {
    companion object {
        const val MAX_PARTY_SIZE = 2
    }
    
    init {
        // リーダーをメンバーリストに追加
        if (!members.contains(leaderName)) {
            members.add(leaderName)
        }
    }
    
    /**
     * メンバー数を取得
     */
    fun getMemberCount(): Int = members.size
    
    /**
     * パーティーが満員かどうかを確認
     */
    fun isFull(): Boolean = members.size >= MAX_PARTY_SIZE
    
    /**
     * 指定したプレイヤーがメンバーかどうかを確認
     */
    fun isMember(playerName: String): Boolean = members.contains(playerName)
    
    /**
     * 指定したプレイヤーがリーダーかどうかを確認
     */
    fun isLeader(playerName: String): Boolean = leaderName == playerName
    
    /**
     * メンバーを追加
     * @param playerName 追加するプレイヤー名
     * @return 追加成功時true、失敗時false
     */
    fun addMember(playerName: String): Boolean {
        if (isFull() || isMember(playerName)) {
            return false
        }
        return members.add(playerName)
    }
    
    /**
     * メンバーを削除
     * @param playerName 削除するプレイヤー名
     * @return 削除成功時true、失敗時false
     */
    fun removeMember(playerName: String): Boolean {
        // リーダーは削除できない
        if (isLeader(playerName)) {
            return false
        }
        return members.remove(playerName)
    }
    
    /**
     * 他のメンバーのリストを取得（自分以外）
     * @param excludePlayerName 除外するプレイヤー名
     * @return 他のメンバーのリスト
     */
    fun getOtherMembers(excludePlayerName: String): List<String> {
        return members.filter { it != excludePlayerName }
    }
    
    /**
     * オンラインメンバーのリストを取得
     * @return オンラインのメンバーのPlayerオブジェクトリスト
     */
    fun getOnlineMembers(): List<Player> {
        return members.mapNotNull { memberName ->
            org.bukkit.Bukkit.getPlayer(memberName)
        }.filter { it.isOnline }
    }
    
    /**
     * パーティーを解散
     */
    fun disband() {
        status = PartyStatus.DISBANDED
        members.clear()
    }
    
    /**
     * パーティーがアクティブかどうかを確認
     */
    fun isActive(): Boolean = status == PartyStatus.ACTIVE
    
    /**
     * パーティー情報の表示用文字列を生成
     */
    fun getDisplayInfo(): String {
        val membersList = members.joinToString(", ")
        val roleText = role?.let { " (${it.name})" } ?: ""
        return "§6[パーティー]$roleText §7リーダー: §e$leaderName §7メンバー: §f$membersList §7(${getMemberCount()}/${MAX_PARTY_SIZE})"
    }
}