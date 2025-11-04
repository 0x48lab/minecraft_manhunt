package com.hacklab.manhunt

import org.bukkit.entity.Player

/**
 * ロール遷移の結果を表すsealed class
 */
sealed class TransitionResult {
    /**
     * ロール遷移成功
     * @param oldRole 遷移前のロール
     * @param newRole 遷移後のロール
     */
    data class Success(
        val oldRole: PlayerRole,
        val newRole: PlayerRole
    ) : TransitionResult()

    /**
     * ロール遷移失敗
     * @param reason 失敗理由
     */
    data class Error(
        val reason: String
    ) : TransitionResult()
}

/**
 * ロール遷移を管理するハンドラー
 * プレイヤーのロール変更時の複雑な処理を一元管理する
 *
 * このクラスは、ゲーム中のプレイヤーロール変更を安全かつ確実に実行するために、
 * 以下の状態遷移フローを提供します:
 *
 * 状態遷移フロー:
 * 1. バリデーション - プレイヤーがオンラインか、ゲームに参加しているか、同じロールでないか
 * 2. 古いロール状態のクリア - コンパス追跡解除、リスポーンタイマーキャンセル等
 * 3. GameManagerでロール更新 - 内部状態の変更
 * 4. TeamManagerでチーム更新 - 名前の色変更
 * 5. 新しいロール状態の適用 - コンパス付与、ゲームモード設定、即座リスポーン等
 * 6. バディー関係の確認と解除 - クロスチームバディーの自動解除
 * 7. UI更新 - スコアボード、アクションバーの再表示
 * 8. ブロードキャスト - 全プレイヤーへの通知
 *
 * エッジケース対応:
 * - 死亡中のプレイヤーがハンターに変更された場合、即座にリスポーン
 * - リスポーン待機中のプレイヤーの場合、待機タイマーをキャンセル
 * - バディー関係が異なるチーム間になった場合、自動的に解除
 * - コンパス追跡中にロール変更した場合、追跡をクリア
 * - 全ての例外を適切にキャッチし、ログに記録
 *
 * 使用例:
 * ```kotlin
 * val result = roleTransitionHandler.transitionRole(player, PlayerRole.HUNTER, admin)
 * when (result) {
 *     is TransitionResult.Success -> {
 *         sender.sendMessage("Role changed from ${result.oldRole} to ${result.newRole}")
 *     }
 *     is TransitionResult.Error -> {
 *         sender.sendMessage(result.reason)
 *     }
 * }
 * ```
 */
class RoleTransitionHandler(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val teamManager: TeamManager,
    private val uiManager: UIManager,
    private val messageManager: MessageManager
) {

    /**
     * ロール遷移を実行する
     * @param player ターゲットプレイヤー
     * @param newRole 新しいロール
     * @param admin 管理者プレイヤー
     * @return 遷移結果
     */
    fun transitionRole(player: Player, newRole: PlayerRole, admin: Player): TransitionResult {
        // バリデーション: プレイヤーがオンラインか
        if (!player.isOnline) {
            return TransitionResult.Error(messageManager.getMessage(admin, "errors.player_not_found"))
        }

        // バリデーション: プレイヤーがゲームに参加しているか
        val oldRole = gameManager.getPlayerRole(player)
        if (oldRole == null) {
            return TransitionResult.Error(messageManager.getMessage(admin, "errors.player_not_in_game"))
        }

        // 最適化: すでに同じロールの場合
        if (oldRole == newRole) {
            return TransitionResult.Error(messageManager.getMessage(admin, "errors.same_role"))
        }

        try {
            // 1. 古いロールの状態をクリア
            clearRoleState(player, oldRole)

            // 2. GameManagerでロールを更新
            gameManager.setPlayerRole(player, newRole)

            // 3. TeamManagerでチームを更新
            teamManager.assignTeam(player, newRole)

            // 4. 新しいロールの状態を適用
            applyRoleState(player, newRole)

            // 5. バディー関係のチェックと解除 (T040-T041)
            checkAndRemoveCrossTeamBuddies(player, oldRole, newRole)

            // 6. UIを更新 (T042)
            uiManager.updateScoreboard(player)
            uiManager.updateActionBar(player)

            // 7. ゲーム統計を更新 (T043)
            // 今後のアクションは新しいロールで記録される
            // （GameManagerが自動的に処理）

            // 8. ロール変更を全員に通知
            broadcastRoleChange(player, oldRole, newRole, admin)

            // 9. 成功ログを記録 (T046)
            plugin.logger.info("Role transition successful: ${player.name} changed from ${oldRole.name} to ${newRole.name} by ${admin.name}")

            return TransitionResult.Success(oldRole, newRole)

        } catch (e: Exception) {
            plugin.logger.warning("Role transition failed for ${player.name}: ${e.message}")
            return TransitionResult.Error("Internal error: ${e.message}")
        }
    }

    /**
     * 古いロールの状態をクリアする
     */
    private fun clearRoleState(player: Player, oldRole: PlayerRole) {
        when (oldRole) {
            PlayerRole.HUNTER -> {
                // コンパス追跡をクリア
                try {
                    plugin.getCompassTracker().clearTarget(player)
                } catch (e: Exception) {
                    plugin.logger.warning("Error clearing compass target: ${e.message}")
                }

                // コンパスアイテムを削除
                player.inventory.contents.forEachIndexed { index, item ->
                    if (item?.type == org.bukkit.Material.COMPASS) {
                        player.inventory.setItem(index, null)
                    }
                }
            }

            PlayerRole.RUNNER -> {
                // 近接警告を削除
                // （GameManager内で管理されているため、特別な処理不要）

                // リスポーンタイマーをキャンセル (T036)
                try {
                    gameManager.cancelRespawnTimer(player)
                } catch (e: Exception) {
                    plugin.logger.warning("Error canceling respawn timer: ${e.message}")
                }
            }

            PlayerRole.SPECTATOR -> {
                // 観戦者は特にクリアする状態がない
            }
        }
    }

    /**
     * 新しいロールの状態を適用する
     */
    private fun applyRoleState(player: Player, newRole: PlayerRole) {
        when (newRole) {
            PlayerRole.HUNTER -> {
                // コンパスアイテムを付与
                try {
                    plugin.getCompassTracker().giveCompass(player)
                } catch (e: Exception) {
                    plugin.logger.warning("Error giving compass: ${e.message}")
                }

                // コンパス追跡を初期化
                // （CompassTrackerが自動的に処理）

                // ゲームモードをサバイバルに設定
                if (player.gameMode != org.bukkit.GameMode.SURVIVAL) {
                    player.gameMode = org.bukkit.GameMode.SURVIVAL
                }

                // 死亡中にハンターに変更された場合、即座にリスポーン (T037)
                if (player.isDead || player.gameMode == org.bukkit.GameMode.SPECTATOR) {
                    try {
                        gameManager.immediateRespawn(player)
                    } catch (e: Exception) {
                        plugin.logger.warning("Error triggering immediate respawn: ${e.message}")
                    }
                }
            }

            PlayerRole.RUNNER -> {
                // 近接警告を有効化
                // （GameManagerのproximity checkが自動的に処理）

                // ゲームモードをサバイバルに設定
                if (player.gameMode != org.bukkit.GameMode.SURVIVAL) {
                    player.gameMode = org.bukkit.GameMode.SURVIVAL
                }

                // タイム制リスポーンルール適用
                // （GameManagerのrespawn処理で自動的に適用される）
            }

            PlayerRole.SPECTATOR -> {
                // ゲームモードをスペクテイターに設定
                player.gameMode = org.bukkit.GameMode.SPECTATOR

                // すべてのゲームメカニクスを無効化
                // （GameManagerとUIManagerが自動的に処理）
            }
        }
    }

    /**
     * バディー関係のチェックと異なるチーム間のバディー解除 (T040-T041)
     */
    private fun checkAndRemoveCrossTeamBuddies(player: Player, oldRole: PlayerRole, newRole: PlayerRole) {
        // ロール変更によってチームが変わった場合のみ処理
        if (oldRole == newRole) return

        try {
            val buddySystem = plugin.getBuddySystem()
            val buddy = buddySystem.getBuddy(player)

            // バディーがいる場合
            if (buddy != null) {
                val buddyRole = gameManager.getPlayerRole(buddy)

                // バディーのロールが取得できて、かつ異なるロールになった場合
                if (buddyRole != null && buddyRole != newRole) {
                    // クロスチームバディーとなったため解除
                    buddySystem.removeBuddy(player)

                    // 両プレイヤーに通知
                    player.sendMessage(messageManager.getMessage(player, "buddy.removed-due-to-role-change"))
                    buddy.sendMessage(messageManager.getMessage(buddy, "buddy.removed-due-to-role-change"))

                    plugin.logger.info("Buddy relationship removed between ${player.name} and ${buddy.name} due to role change")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error checking/removing cross-team buddies: ${e.message}")
        }
    }

    /**
     * ロール変更を全プレイヤーに通知する
     */
    private fun broadcastRoleChange(player: Player, oldRole: PlayerRole, newRole: PlayerRole, admin: Player) {
        val oldRoleName = when (oldRole) {
            PlayerRole.HUNTER -> messageManager.getMessage(admin, "role.hunter")
            PlayerRole.RUNNER -> messageManager.getMessage(admin, "role.runner")
            PlayerRole.SPECTATOR -> messageManager.getMessage(admin, "role.spectator")
        }

        val newRoleName = when (newRole) {
            PlayerRole.HUNTER -> messageManager.getMessage(admin, "role.hunter")
            PlayerRole.RUNNER -> messageManager.getMessage(admin, "role.runner")
            PlayerRole.SPECTATOR -> messageManager.getMessage(admin, "role.spectator")
        }

        val message = messageManager.getMessage(
            admin,
            "commands.admin.role_change_broadcast",
            "admin" to admin.name,
            "player" to player.name,
            "oldRole" to oldRoleName,
            "newRole" to newRoleName
        )

        org.bukkit.Bukkit.broadcastMessage(message)
    }
}
