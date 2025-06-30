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
    private val gameManager: GameManager,
    private val messageManager: MessageManager
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "teamchat.player-only"))
            return true
        }
        
        // ゲーム中のみ使用可能
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage(messageManager.getMessage(sender, "teamchat.game-only"))
            return true
        }
        
        val senderRole = gameManager.getPlayerRole(sender)
        if (senderRole == null || senderRole == PlayerRole.SPECTATOR) {
            sender.sendMessage(messageManager.getMessage(sender, "teamchat.spectator-cannot-use"))
            return true
        }
        
        if (args.isEmpty()) {
            sender.sendMessage(messageManager.getMessage(sender, "teamchat.usage"))
            sender.sendMessage(messageManager.getMessage(sender, "teamchat.usage-hint"))
            return true
        }
        
        // メッセージを結合
        val message = args.joinToString(" ")
        if (message.isBlank()) {
            sender.sendMessage(messageManager.getMessage(sender, "teamchat.empty-message"))
            return true
        }
        
        // 味方プレイヤーを取得
        val teammates = getTeammates(sender, senderRole)
        
        if (teammates.isEmpty()) {
            sender.sendMessage(messageManager.getMessage(sender, "teamchat.no-teammates"))
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
            PlayerRole.HUNTER -> messageManager.getMessage(sender, "teamchat.hunter-prefix")
            PlayerRole.RUNNER -> messageManager.getMessage(sender, "teamchat.runner-prefix")
            PlayerRole.SPECTATOR -> "" // 実際は使用されない
        }
        
        val formattedMessage = messageManager.getMessage(sender, "teamchat.format", 
            "prefix" to rolePrefix, 
            "sender" to sender.name, 
            "message" to message
        )
        
        // 送信者自身にも表示
        sender.sendMessage(formattedMessage)
        
        // チームメンバーに送信
        teammates.forEach { teammate ->
            // 各チームメイトの言語設定に応じたメッセージを送信
            val teammateMessage = messageManager.getMessage(teammate, "teamchat.format",
                "prefix" to rolePrefix,
                "sender" to sender.name,
                "message" to message
            )
            teammate.sendMessage(teammateMessage)
        }
        
        // ログに記録
        gameManager.getPlugin().logger.info("TeamChat [${senderRole.name}] ${sender.name}: $message (${teammates.size} recipients)")
    }
}