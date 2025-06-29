package com.hacklab.manhunt

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * 味方同士でのチャット機能を提供するクラス
 * /r <メッセージ> コマンドで同じ役割のプレイヤー間でのみチャット可能
 */
class TeamChatCommand(
    private val gameManager: GameManager
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。")
            return true
        }
        
        // ゲーム中のみ使用可能
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage("§cチームチャットはゲーム中のみ使用できます。")
            return true
        }
        
        val senderRole = gameManager.getPlayerRole(sender)
        if (senderRole == null || senderRole == PlayerRole.SPECTATOR) {
            sender.sendMessage("§c観戦者はチームチャットを使用できません。")
            return true
        }
        
        if (args.isEmpty()) {
            sender.sendMessage("§c使用法: /r <メッセージ>")
            sender.sendMessage("§7味方同士でのみメッセージを送信します。")
            return true
        }
        
        // メッセージを結合
        val message = args.joinToString(" ")
        if (message.isBlank()) {
            sender.sendMessage("§cメッセージが空です。")
            return true
        }
        
        // 味方プレイヤーを取得
        val teammates = getTeammates(sender, senderRole)
        
        if (teammates.isEmpty()) {
            sender.sendMessage("§c現在チームメンバーがいません。")
            return true
        }
        
        // チームチャットメッセージを送信
        sendTeamMessage(sender, senderRole, message, teammates)
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        // チームチャットはフリーテキストなのでTab補完なし
        return emptyList()
    }
    
    /**
     * 同じ役割のチームメンバーを取得
     */
    private fun getTeammates(sender: Player, senderRole: PlayerRole): List<Player> {
        return when (senderRole) {
            PlayerRole.HUNTER -> gameManager.getAllHunters()
            PlayerRole.RUNNER -> gameManager.getAllRunners()
            PlayerRole.SPECTATOR -> emptyList()
        }.filter { 
            it.isOnline && it != sender 
        }
    }
    
    /**
     * チームメッセージを送信
     */
    private fun sendTeamMessage(sender: Player, senderRole: PlayerRole, message: String, teammates: List<Player>) {
        val rolePrefix = when (senderRole) {
            PlayerRole.HUNTER -> "§c[🗡チーム]"
            PlayerRole.RUNNER -> "§a[🏃チーム]"
            PlayerRole.SPECTATOR -> "§7[👁観戦]" // 実際は使用されない
        }
        
        val formattedMessage = "$rolePrefix §f${sender.name}: §7$message"
        
        // 送信者自身にも表示
        sender.sendMessage(formattedMessage)
        
        // チームメンバーに送信
        teammates.forEach { teammate ->
            teammate.sendMessage(formattedMessage)
        }
        
        // ログに記録
        gameManager.plugin.logger.info("TeamChat [${senderRole.name}] ${sender.name}: $message (${teammates.size} recipients)")
    }
}