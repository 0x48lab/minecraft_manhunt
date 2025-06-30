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
                        sender.sendMessage("§6===============================")
                        sender.sendMessage("§e所持金: §f${balance}${unit}")
                        sender.sendMessage("§6===============================")
                    }
                    "help" -> {
                        // ヘルプ表示
                        showHelp(sender)
                    }
                    else -> {
                        sender.sendMessage("§c不明なサブコマンドです。/shop help でヘルプを確認してください。")
                    }
                }
            }
            else -> {
                sender.sendMessage("§c引数が多すぎます。/shop help でヘルプを確認してください。")
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
        player.sendMessage("§6=== ショップコマンド ヘルプ ===")
        player.sendMessage("§e/shop §7- ショップを開く")
        player.sendMessage("§e/shop balance §7- 所持金を確認")
        player.sendMessage("§e/shop help §7- このヘルプを表示")
        player.sendMessage("")
        player.sendMessage("§7ゲーム中にコインを獲得して")
        player.sendMessage("§7様々なアイテムを購入できます。")
        
        val role = plugin.getGameManager().getPlayerRole(player)
        when (role) {
            PlayerRole.HUNTER -> {
                player.sendMessage("")
                player.sendMessage("§c[ハンター] コイン獲得方法:")
                player.sendMessage("§7- ランナーにダメージを与える")
                player.sendMessage("§7- ランナーを倒す")
                player.sendMessage("§7- 時間経過ボーナス")
            }
            PlayerRole.RUNNER -> {
                player.sendMessage("")
                player.sendMessage("§a[ランナー] コイン獲得方法:")
                player.sendMessage("§7- 生存時間ボーナス")
                player.sendMessage("§7- ディメンション到達")
                player.sendMessage("§7- ダイヤモンド収集")
                player.sendMessage("§7- ハンターから逃走成功")
            }
            else -> {}
        }
    }
}