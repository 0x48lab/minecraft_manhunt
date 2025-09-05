package com.hacklab.manhunt

import com.hacklab.manhunt.economy.EconomyManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.cos
import kotlin.math.sin

/**
 * 味方プレイヤーへのテレポート機能を提供するクラス
 * /warp <プレイヤー名> コマンドで100Gを消費して5秒後にテレポート
 */
class WarpCommand(
    private val gameManager: GameManager,
    private val messageManager: MessageManager,
    private val economyManager: EconomyManager
) : CommandExecutor, TabCompleter {
    
    companion object {
        private const val WARP_COST = 50
        private const val WARP_DELAY_SECONDS = 5
        private const val MOVEMENT_THRESHOLD = 0.1
    }
    
    // 進行中のワープを管理
    private val activeWarps = mutableMapOf<Player, WarpTask>()
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "warp.player-only"))
            return true
        }
        
        // ゲーム中のみ使用可能
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.game-only"))
            return true
        }
        
        val senderRole = gameManager.getPlayerRole(sender)
        if (senderRole == null || senderRole == PlayerRole.SPECTATOR) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.spectator-cannot-use"))
            return true
        }
        
        if (args.isEmpty()) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.usage"))
            return true
        }
        
        val targetName = args[0]
        val target = Bukkit.getPlayer(targetName)
        
        if (target == null || !target.isOnline) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.player-not-found", "player" to targetName))
            return true
        }
        
        if (target == sender) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.cannot-warp-self"))
            return true
        }
        
        val targetRole = gameManager.getPlayerRole(target)
        if (targetRole != senderRole) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.not-teammate", "player" to target.name))
            return true
        }
        
        // 残高チェック
        val currentBalance = economyManager.getBalance(sender)
        if (currentBalance < WARP_COST) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.insufficient-funds", 
                "cost" to WARP_COST,
                "balance" to currentBalance,
                "unit" to economyManager.getCurrencyUnit()
            ))
            return true
        }
        
        // 既に進行中のワープがある場合はキャンセル
        if (activeWarps.containsKey(sender)) {
            sender.sendMessage(messageManager.getMessage(sender, "warp.already-warping"))
            return true
        }
        
        // ワープ開始（コストはワープ成功時に引く）
        startWarp(sender, target)
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player || args.size != 1) {
            return emptyList()
        }
        
        val senderRole = gameManager.getPlayerRole(sender) ?: return emptyList()
        if (senderRole == PlayerRole.SPECTATOR) {
            return emptyList()
        }
        
        // 同じ役割のプレイヤーのみをサジェスト
        val teammates = when (senderRole) {
            PlayerRole.HUNTER -> gameManager.getAllHunters()
            PlayerRole.RUNNER -> gameManager.getAllRunners()
            PlayerRole.SPECTATOR -> emptyList()
        }.filter { 
            it.isOnline && it != sender && it.name.lowercase().startsWith(args[0].lowercase())
        }.map { it.name }
        
        return teammates
    }
    
    private fun startWarp(player: Player, target: Player) {
        val warpTask = WarpTask(player, target, player.location.clone())
        activeWarps[player] = warpTask
        
        // 開始メッセージ
        player.sendMessage(messageManager.getMessage(player, "warp.starting",
            "target" to target.name,
            "seconds" to WARP_DELAY_SECONDS
        ))
        target.sendMessage(messageManager.getMessage(target, "warp.incoming",
            "player" to player.name
        ))
        
        // カウントダウンタスク
        object : BukkitRunnable() {
            var countdown = WARP_DELAY_SECONDS
            
            override fun run() {
                // プレイヤーがオフラインになった場合
                if (!player.isOnline || !target.isOnline) {
                    cancelWarp(player, "warp.cancelled-offline")
                    return
                }
                
                // 移動チェック
                val currentLocation = player.location
                val distance = currentLocation.distance(warpTask.startLocation)
                if (distance > MOVEMENT_THRESHOLD) {
                    cancelWarp(player, "warp.cancelled-moved")
                    return
                }
                
                // パーティクル表示
                displayWarpParticles(player)
                
                // カウントダウン
                if (countdown > 0) {
                    if (countdown <= 3) {
                        player.sendTitle(
                            messageManager.getMessage(player, "warp.countdown-title"),
                            messageManager.getMessage(player, "warp.countdown-subtitle", "seconds" to countdown),
                            0, 25, 0
                        )
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (3 - countdown) * 0.2f)
                    }
                    countdown--
                } else {
                    // テレポート実行
                    executeWarp(player, target)
                    cancel()
                }
            }
        }.runTaskTimer(gameManager.getPlugin(), 0L, 20L)
    }
    
    private fun displayWarpParticles(player: Player) {
        val location = player.location.add(0.0, 0.5, 0.0)
        val radius = 1.5
        val particles = 30
        
        // 円形にポータルパーティクルを表示
        for (i in 0 until particles) {
            val angle = 2 * Math.PI * i / particles
            val x = radius * cos(angle)
            val z = radius * sin(angle)
            
            val particleLocation = location.clone().add(x, 0.0, z)
            player.world.spawnParticle(
                Particle.PORTAL,
                particleLocation,
                3,
                0.0, 0.0, 0.0,
                0.5
            )
        }
        
        // 上昇するパーティクル
        for (y in 0..2) {
            player.world.spawnParticle(
                Particle.PORTAL,
                location.clone().add(0.0, y.toDouble(), 0.0),
                5,
                0.2, 0.0, 0.2,
                0.2
            )
        }
    }
    
    private fun executeWarp(player: Player, target: Player) {
        activeWarps.remove(player)
        
        // テレポート前の位置を記録
        val fromLocation = player.location.clone()
        
        // 安全な位置を探す
        val targetLocation = findSafeLocation(target.location)
        
        // テレポート直前にコストを引く（失敗したらキャンセル）
        val moneyRemoved = economyManager.removeMoney(player, WARP_COST)
        if (!moneyRemoved) {
            // お金が足りない場合はワープ失敗
            player.sendMessage(messageManager.getMessage(player, "warp.insufficient-funds", 
                "cost" to WARP_COST,
                "balance" to economyManager.getBalance(player),
                "unit" to economyManager.getCurrencyUnit()
            ))
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f)
            return
        }
        
        // コスト引き落とし成功メッセージ
        player.sendMessage(messageManager.getMessage(player, "warp.cost-deducted",
            "cost" to WARP_COST,
            "unit" to economyManager.getCurrencyUnit()
        ))
        
        // テレポート実行
        player.teleport(targetLocation)
        
        // エフェクト
        player.world.spawnParticle(Particle.PORTAL, fromLocation, 50, 0.5, 1.0, 0.5, 0.1)
        player.world.spawnParticle(Particle.PORTAL, targetLocation, 50, 0.5, 1.0, 0.5, 0.1)
        
        player.playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
        target.playSound(targetLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f)
        
        // 完了メッセージ
        player.sendMessage(messageManager.getMessage(player, "warp.success", "target" to target.name))
        target.sendMessage(messageManager.getMessage(target, "warp.arrived", "player" to player.name))
    }
    
    private fun cancelWarp(player: Player, messageKey: String) {
        activeWarps.remove(player)?.let {
            player.sendMessage(messageManager.getMessage(player, messageKey))
            // キャンセル音
            player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f)
        }
    }
    
    private fun findSafeLocation(location: Location): Location {
        val world = location.world ?: return location
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        
        // ターゲットの位置から安全な場所を探す
        for (dy in 0..2) {
            for (dx in -1..1) {
                for (dz in -1..1) {
                    val checkLoc = Location(world, x + dx + 0.5, (y + dy).toDouble(), z + dz + 0.5)
                    val block = checkLoc.block
                    val blockAbove = checkLoc.clone().add(0.0, 1.0, 0.0).block
                    
                    // 足元が固体ブロックで、上2ブロックが空気なら安全
                    if (block.type.isSolid && 
                        !blockAbove.type.isSolid && 
                        !checkLoc.clone().add(0.0, 2.0, 0.0).block.type.isSolid) {
                        return checkLoc.clone().add(0.0, 1.0, 0.0)
                    }
                }
            }
        }
        
        // 安全な場所が見つからない場合は元の位置の少し上
        return location.clone().add(0.0, 1.0, 0.0)
    }
    
    // プレイヤーがログアウトした時のクリーンアップ
    fun onPlayerQuit(player: Player) {
        activeWarps.remove(player)
    }
    
    // ゲーム終了時のクリーンアップ
    fun onGameEnd() {
        activeWarps.clear()
    }
    
    private data class WarpTask(
        val player: Player,
        val target: Player,
        val startLocation: Location
    )
}