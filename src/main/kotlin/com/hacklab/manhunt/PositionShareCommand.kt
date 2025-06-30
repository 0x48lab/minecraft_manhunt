package com.hacklab.manhunt

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * 味方同士で座標を共有する機能を提供するクラス
 * /pos コマンドで現在の座標を同じ役割のプレイヤーに送信
 */
class PositionShareCommand(
    private val gameManager: GameManager,
    private val messageManager: MessageManager
) : CommandExecutor {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "position.player-only"))
            return true
        }
        
        // ゲーム中のみ使用可能
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage(messageManager.getMessage(sender, "position.game-only"))
            return true
        }
        
        val senderRole = gameManager.getPlayerRole(sender)
        if (senderRole == null || senderRole == PlayerRole.SPECTATOR) {
            sender.sendMessage(messageManager.getMessage(sender, "position.spectator-cannot-use"))
            return true
        }
        
        // 現在の座標を取得
        val location = sender.location
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        val world = location.world?.name ?: "unknown"
        
        // 味方プレイヤーを取得
        val teammates = getTeammates(sender, senderRole)
        
        if (teammates.isEmpty()) {
            sender.sendMessage(messageManager.getMessage(sender, "position.no-teammates"))
            return true
        }
        
        // 座標メッセージを送信
        sendPositionMessage(sender, senderRole, x, y, z, world, teammates)
        
        return true
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
     * 座標メッセージを送信
     */
    private fun sendPositionMessage(
        sender: Player, 
        senderRole: PlayerRole, 
        x: Int, 
        y: Int, 
        z: Int, 
        world: String,
        teammates: List<Player>
    ) {
        val rolePrefix = when (senderRole) {
            PlayerRole.HUNTER -> messageManager.getMessage(sender, "position.hunter-prefix")
            PlayerRole.RUNNER -> messageManager.getMessage(sender, "position.runner-prefix")
            PlayerRole.SPECTATOR -> "" // 実際は使用されない
        }
        
        // 送信者自身にも表示（確認用）
        val sentMessage = messageManager.getMessage(sender, "position.sent-format",
            "prefix" to rolePrefix,
            "sender" to sender.name,
            "x" to x,
            "y" to y,
            "z" to z,
            "world" to world,
            "count" to teammates.size
        )
        sender.sendMessage(sentMessage)
        
        // チームメンバーに送信
        teammates.forEach { teammate ->
            val formattedMessage = messageManager.getMessage(teammate, "position.format",
                "prefix" to rolePrefix,
                "sender" to sender.name,
                "x" to x,
                "y" to y,
                "z" to z,
                "world" to world
            )
            teammate.sendMessage(formattedMessage)
            
            // 相対座標も表示（便利機能）
            if (teammate.world == sender.world) {
                val deltaX = x - teammate.location.blockX
                val deltaY = y - teammate.location.blockY
                val deltaZ = z - teammate.location.blockZ
                val distance = teammate.location.distance(sender.location).toInt()
                
                val relativeX = if (deltaX >= 0) "+$deltaX" else "$deltaX"
                val relativeY = if (deltaY >= 0) "+$deltaY" else "$deltaY"
                val relativeZ = if (deltaZ >= 0) "+$deltaZ" else "$deltaZ"
                
                val relativeMessage = messageManager.getMessage(teammate, "position.relative",
                    "x" to relativeX,
                    "y" to relativeY,
                    "z" to relativeZ,
                    "distance" to distance
                )
                teammate.sendMessage(relativeMessage)
            } else {
                teammate.sendMessage(messageManager.getMessage(teammate, "position.different-world"))
            }
        }
        
        // ログに記録
        gameManager.getPlugin().logger.info("Position Share [${senderRole.name}] ${sender.name}: X:$x Y:$y Z:$z in $world (${teammates.size} recipients)")
    }
}