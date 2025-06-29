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
    private val gameManager: GameManager
) : CommandExecutor {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。")
            return true
        }
        
        // ゲーム中のみ使用可能
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage("§c座標共有はゲーム中のみ使用できます。")
            return true
        }
        
        val senderRole = gameManager.getPlayerRole(sender)
        if (senderRole == null || senderRole == PlayerRole.SPECTATOR) {
            sender.sendMessage("§c観戦者は座標共有を使用できません。")
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
            sender.sendMessage("§c現在チームメンバーがいません。")
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
            PlayerRole.HUNTER -> "§c[🗡座標]"
            PlayerRole.RUNNER -> "§a[🏃座標]"
            PlayerRole.SPECTATOR -> "§7[👁座標]" // 実際は使用されない
        }
        
        // 座標をクリック可能な形式で作成
        val formattedMessage = "$rolePrefix §f${sender.name}: §bX:$x Y:$y Z:$z §7($world)"
        
        // 送信者自身にも表示（確認用）
        sender.sendMessage("$formattedMessage §7(味方${teammates.size}人に送信)")
        
        // チームメンバーに送信
        teammates.forEach { teammate ->
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
                
                teammate.sendMessage("§7  └→ 相対座標: X:$relativeX Y:$relativeY Z:$relativeZ (距離: ${distance}m)")
            } else {
                teammate.sendMessage("§7  └→ §e別ワールドにいます")
            }
        }
        
        // ログに記録
        gameManager.plugin.logger.info("Position Share [${senderRole.name}] ${sender.name}: X:$x Y:$y Z:$z in $world (${teammates.size} recipients)")
    }
}