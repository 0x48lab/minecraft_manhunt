package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * バディー（相棒）システムのコマンド処理クラス
 * /buddy invite, accept, decline, remove, status
 */
class BuddyCommand(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val buddySystem: BuddySystem,
    private val messageManager: MessageManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage("command.only-players"))
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "invite" -> handleInvite(sender, args)
            "accept" -> handleAccept(sender, args)
            "decline" -> handleDecline(sender, args)
            "remove" -> handleRemove(sender)
            "status" -> handleStatus(sender)
            "help" -> showHelp(sender)
            else -> showHelp(sender)
        }

        return true
    }

    private fun handleInvite(sender: Player, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.command.invite-usage"))
            return
        }

        val targetName = args[1]
        val target = Bukkit.getPlayer(targetName)
        
        if (target == null) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.player-not-found", 
                "player" to targetName))
            return
        }

        buddySystem.sendBuddyInvite(sender, target)
    }

    private fun handleAccept(sender: Player, args: Array<out String>) {
        if (args.size < 2) {
            // 受信した招待一覧を表示
            val incomingInvites = buddySystem.getIncomingInvites(sender)
            if (incomingInvites.isEmpty()) {
                sender.sendMessage(messageManager.getMessage(sender, "buddy.no-pending-invites"))
                return
            }

            sender.sendMessage(messageManager.getMessage(sender, "buddy.pending-invites-header"))
            incomingInvites.forEach { inviter ->
                sender.sendMessage(messageManager.getMessage(sender, "buddy.pending-invite-item", 
                    "player" to inviter.name))
            }
            sender.sendMessage(messageManager.getMessage(sender, "buddy.command.accept-usage"))
            return
        }

        val senderName = args[1]
        val senderPlayer = Bukkit.getPlayer(senderName)
        
        if (senderPlayer == null) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.player-not-found", 
                "player" to senderName))
            return
        }

        buddySystem.acceptBuddyInvite(sender, senderPlayer)
    }

    private fun handleDecline(sender: Player, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.command.decline-usage"))
            return
        }

        val senderName = args[1]
        val senderPlayer = Bukkit.getPlayer(senderName)
        
        if (senderPlayer == null) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.player-not-found", 
                "player" to senderName))
            return
        }

        buddySystem.declineBuddyInvite(sender, senderPlayer)
    }

    private fun handleRemove(sender: Player) {
        buddySystem.removeBuddy(sender)
    }

    private fun handleStatus(sender: Player) {
        val buddy = buddySystem.getBuddy(sender)
        if (buddy != null) {
            val coordinates = buddySystem.getBuddyRelativeCoordinates(sender)
            sender.sendMessage(messageManager.getMessage(sender, "buddy.status-has-buddy", 
                "buddy" to buddy.name, "coordinates" to (coordinates ?: "Unknown")))
        } else {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.status-no-buddy"))
        }

        // 送信中の招待をチェック
        val pendingTarget = buddySystem.hasPendingInvite(sender)
        if (pendingTarget != null) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.status-pending-sent", 
                "player" to pendingTarget.name))
        }

        // 受信した招待をチェック
        val incomingInvites = buddySystem.getIncomingInvites(sender)
        if (incomingInvites.isNotEmpty()) {
            sender.sendMessage(messageManager.getMessage(sender, "buddy.status-pending-received", 
                "count" to incomingInvites.size))
        }
    }

    private fun showHelp(sender: Player) {
        sender.sendMessage(messageManager.getMessage(sender, "buddy.command.help-header"))
        sender.sendMessage(messageManager.getMessage(sender, "buddy.command.help-invite"))
        sender.sendMessage(messageManager.getMessage(sender, "buddy.command.help-accept"))
        sender.sendMessage(messageManager.getMessage(sender, "buddy.command.help-decline"))
        sender.sendMessage(messageManager.getMessage(sender, "buddy.command.help-remove"))
        sender.sendMessage(messageManager.getMessage(sender, "buddy.command.help-status"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()

        return when (args.size) {
            1 -> {
                listOf("invite", "accept", "decline", "remove", "status", "help")
                    .filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "invite" -> {
                        // 同じチームのプレイヤーのみ提案
                        val senderRole = gameManager.getPlayerRole(sender)
                        if (senderRole != null && senderRole != PlayerRole.SPECTATOR) {
                            Bukkit.getOnlinePlayers()
                                .filter { player ->
                                    player != sender &&
                                    gameManager.getPlayerRole(player) == senderRole &&
                                    buddySystem.getBuddy(player) == null &&
                                    player.name.lowercase().startsWith(args[1].lowercase())
                                }
                                .map { it.name }
                        } else {
                            emptyList()
                        }
                    }
                    "accept", "decline" -> {
                        // 招待を送ってきたプレイヤーのみ提案
                        buddySystem.getIncomingInvites(sender)
                            .filter { it.name.lowercase().startsWith(args[1].lowercase()) }
                            .map { it.name }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
