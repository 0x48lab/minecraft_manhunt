package com.hacklab.manhunt.shop

import com.hacklab.manhunt.GameState
import com.hacklab.manhunt.Main
import com.hacklab.manhunt.MessageManager
import com.hacklab.manhunt.PlayerRole
import com.hacklab.manhunt.economy.EconomyManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * ショップコマンドの実装
 */
class ShopCommand(
    private val plugin: Main,
    private val shopManager: ShopManager,
    private val economyManager: EconomyManager,
    private val messageManager: MessageManager
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "shop.player-only"))
            return true
        }
        
        // ゲーム状態チェック
        if (plugin.getGameManager().getGameState() != GameState.RUNNING) {
            sender.sendMessage(messageManager.getMessage(sender, "shop.game-only"))
            return true
        }
        
        // 役割チェック
        val role = plugin.getGameManager().getPlayerRole(sender)
        if (role == null || role == PlayerRole.SPECTATOR) {
            sender.sendMessage(messageManager.getMessage(sender, "shop.spectator-denied"))
            return true
        }
        
        when (args.size) {
            0 -> {
                // ショップを開く
                shopManager.openShop(sender)
            }
            1 -> {
                when (args[0].lowercase()) {
                    "balance", "money" -> {
                        // 残高確認
                        val balance = economyManager.getBalance(sender)
                        val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
                        sender.sendMessage(messageManager.getMessage(sender, "shop.balance-header"))
                        sender.sendMessage(messageManager.getMessage(sender, "shop.balance-display", mapOf("balance" to balance, "unit" to unit)))
                        sender.sendMessage(messageManager.getMessage(sender, "shop.balance-footer"))
                    }
                    "help" -> {
                        // ヘルプ表示
                        showHelp(sender)
                    }
                    else -> {
                        sender.sendMessage(messageManager.getMessage(sender, "shop.unknown-subcommand"))
                    }
                }
            }
            else -> {
                sender.sendMessage(messageManager.getMessage(sender, "shop.too-many-args"))
            }
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        
        return when (args.size) {
            1 -> {
                val subcommands = listOf("balance", "money", "help")
                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            else -> emptyList()
        }
    }
    
    private fun showHelp(player: Player) {
        player.sendMessage(messageManager.getMessage(player, "shop.help-header"))
        player.sendMessage(messageManager.getMessage(player, "shop.help-open"))
        player.sendMessage(messageManager.getMessage(player, "shop.help-balance"))
        player.sendMessage(messageManager.getMessage(player, "shop.help-help"))
        player.sendMessage("")
        player.sendMessage(messageManager.getMessage(player, "shop.help-description1"))
        player.sendMessage(messageManager.getMessage(player, "shop.help-description2"))
        
        val role = plugin.getGameManager().getPlayerRole(player)
        when (role) {
            PlayerRole.HUNTER -> {
                player.sendMessage("")
                player.sendMessage(messageManager.getMessage(player, "shop.help-hunter-header"))
                player.sendMessage(messageManager.getMessage(player, "shop.help-hunter-damage"))
                player.sendMessage(messageManager.getMessage(player, "shop.help-hunter-kill"))
                player.sendMessage(messageManager.getMessage(player, "shop.help-hunter-time"))
            }
            PlayerRole.RUNNER -> {
                player.sendMessage("")
                player.sendMessage(messageManager.getMessage(player, "shop.help-runner-header"))
                player.sendMessage(messageManager.getMessage(player, "shop.help-runner-survival"))
                player.sendMessage(messageManager.getMessage(player, "shop.help-runner-dimension"))
                player.sendMessage(messageManager.getMessage(player, "shop.help-runner-diamond"))
                player.sendMessage(messageManager.getMessage(player, "shop.help-runner-escape"))
            }
            else -> {}
        }
    }
}