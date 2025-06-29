package com.hacklab.manhunt

import org.bukkit.*
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class VirtualCompass(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val configManager: ConfigManager,
    private val messageManager: MessageManager
) : Listener {
    
    private val cooldowns = mutableMapOf<Player, Long>()
    private val targetIndex = mutableMapOf<Player, Int>() // プレイヤーごとのターゲットインデックス
    private val compassSlot = 0 // ホットバーの最初のスロット
    private val cooldownTime = 1000L // 1秒のクールダウン
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        
        // ゲーム中のハンターのみ
        if (gameManager.getGameState() != GameState.RUNNING || 
            gameManager.getPlayerRole(player) != PlayerRole.HUNTER) {
            return
        }
        
        // 右クリックまたは左クリック
        if (event.action != Action.RIGHT_CLICK_AIR && 
            event.action != Action.RIGHT_CLICK_BLOCK &&
            event.action != Action.LEFT_CLICK_AIR &&
            event.action != Action.LEFT_CLICK_BLOCK) {
            return
        }
        
        // メインハンドが空の場合、または通常のコンパスを持っている場合
        val itemInHand = player.inventory.itemInMainHand
        val isEmptyHand = itemInHand.type == Material.AIR && player.inventory.heldItemSlot == compassSlot
        val isCompass = itemInHand.type == Material.COMPASS
        
        if (!isEmptyHand && !isCompass) {
            return
        }
        
        // クールダウンチェック
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[player] ?: 0
        if (now - lastUse < cooldownTime) {
            val remaining = ((cooldownTime - (now - lastUse)) / 1000.0)
            player.sendMessage(messageManager.getMessage(player, "compass.cooldown", 
                mapOf("time" to String.format("%.1f", remaining))))
            return
        }
        
        // ターゲットを決定（コンパス持ちの場合は循環選択、空手の場合は最寄り）
        val targetRunner = if (isCompass) {
            getNextTarget(player)
        } else {
            findNearestRunner(player)
        }
        
        if (targetRunner == null) {
            player.sendMessage(messageManager.getMessage(player, "compass.no-target"))
            showNoTargetEffect(player)
            return
        }
        
        // 同じワールドかチェック
        if (player.world != targetRunner.world) {
            player.sendMessage(messageManager.getMessage(player, "compass.different-world", 
                mapOf("world" to targetRunner.world.name)))
            showDifferentWorldEffect(player)
            cooldowns[player] = now
            return
        }
        
        // 方向と距離を計算
        val direction = calculateDirection(player, targetRunner)
        val distance = player.location.distance(targetRunner.location)
        
        // ビジュアルフィードバック（ターゲット切り替えの場合は特別表示）
        if (isCompass) {
            val allRunners = getAllValidRunners(player)
            val currentIndex = targetIndex[player] ?: 0
            showTargetSwitchEffect(player, direction, targetRunner, distance, currentIndex + 1, allRunners.size)
        } else {
            showCompassEffect(player, direction, targetRunner, distance)
        }
        
        // クールダウン設定
        cooldowns[player] = now
    }
    
    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        
        // ゲーム中のハンターのみ
        if (gameManager.getGameState() != GameState.RUNNING || 
            gameManager.getPlayerRole(player) != PlayerRole.HUNTER) {
            return
        }
        
        // コンパススロットに切り替えた場合
        if (event.newSlot == compassSlot) {
            // ActionBarでヒントを表示
            val hintMessage = messageManager.getMessage(player, "compass.actionbar-hint")
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(hintMessage))
        }
    }
    
    private fun findNearestRunner(hunter: Player): Player? {
        return getAllValidRunners(hunter)
            .minByOrNull { it.location.distance(hunter.location) }
    }
    
    private fun getAllValidRunners(hunter: Player): List<Player> {
        return gameManager.getAllRunners()
            .filter { it.isOnline && !it.isDead && it.world == hunter.world }
    }
    
    private fun getNextTarget(hunter: Player): Player? {
        val runners = getAllValidRunners(hunter)
        if (runners.isEmpty()) return null
        
        val currentIndex = targetIndex[hunter] ?: 0
        val nextIndex = (currentIndex + 1) % runners.size
        targetIndex[hunter] = nextIndex
        
        return runners[nextIndex]
    }
    
    private fun calculateDirection(from: Player, to: Player): Vector {
        val fromLoc = from.location
        val toLoc = to.location
        
        // 水平方向の角度を計算
        val dx = toLoc.x - fromLoc.x
        val dz = toLoc.z - fromLoc.z
        val angle = atan2(dz, dx) - Math.toRadians(from.location.yaw.toDouble()) - Math.PI / 2
        
        // 方向ベクトルを作成
        return Vector(sin(angle), 0.0, -cos(angle)).normalize()
    }
    
    private fun showCompassEffect(hunter: Player, direction: Vector, target: Player, distance: Double) {
        val formattedDistance = when {
            distance < 10 -> "§c§l${distance.toInt()}m"
            distance < 50 -> "§e§l${distance.toInt()}m"
            else -> "§a§l${distance.toInt()}m"
        }
        
        // タイトル表示
        hunter.sendTitle(
            "§6§l${target.name}",
            "$formattedDistance §7| ${getDirectionArrow(direction)}",
            10, 40, 10
        )
        
        // パーティクル表示
        showDirectionParticles(hunter, direction)
        
        // サウンド再生
        hunter.playSound(hunter.location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f)
        
        // チャットメッセージ
        hunter.sendMessage(messageManager.getMessage(hunter, "compass.tracking", 
            mapOf("player" to target.name, "distance" to formattedDistance)))
    }
    
    private fun showTargetSwitchEffect(hunter: Player, direction: Vector, target: Player, distance: Double, currentIndex: Int, totalTargets: Int) {
        val formattedDistance = when {
            distance < 10 -> "§c§l${distance.toInt()}m"
            distance < 50 -> "§e§l${distance.toInt()}m"
            else -> "§a§l${distance.toInt()}m"
        }
        
        // タイトル表示（ターゲット切り替え情報付き）
        hunter.sendTitle(
            "§b§l[${currentIndex}/${totalTargets}] §6§l${target.name}",
            "$formattedDistance §7| ${getDirectionArrow(direction)} §8| §eターゲット切り替え",
            10, 50, 10
        )
        
        // 特別なパーティクル表示（青色）
        showTargetSwitchParticles(hunter, direction)
        
        // 切り替えサウンド
        hunter.playSound(hunter.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f)
        
        // チャットメッセージ
        hunter.sendMessage(messageManager.getMessage(hunter, "compass.target-switched", 
            mapOf("index" to currentIndex, "total" to totalTargets, "player" to target.name, "distance" to formattedDistance)))
    }
    
    private fun showDirectionParticles(player: Player, direction: Vector) {
        object : BukkitRunnable() {
            var count = 0
            
            override fun run() {
                if (count >= 10) {
                    cancel()
                    return
                }
                
                val loc = player.location.add(0.0, 1.5, 0.0)
                val particleLoc = loc.clone().add(direction.clone().multiply(2.0))
                
                player.world.spawnParticle(
                    Particle.DUST,
                    particleLoc,
                    5,
                    0.1, 0.1, 0.1,
                    0.0,
                    Particle.DustOptions(Color.ORANGE, 1.5f)
                )
                
                count++
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }
    
    private fun showTargetSwitchParticles(player: Player, direction: Vector) {
        object : BukkitRunnable() {
            var count = 0
            
            override fun run() {
                if (count >= 15) {
                    cancel()
                    return
                }
                
                val loc = player.location.add(0.0, 1.5, 0.0)
                val particleLoc = loc.clone().add(direction.clone().multiply(2.0))
                
                // 青色のパーティクル（ターゲット切り替え用）
                player.world.spawnParticle(
                    Particle.DUST,
                    particleLoc,
                    3,
                    0.15, 0.15, 0.15,
                    0.0,
                    Particle.DustOptions(Color.AQUA, 2.0f)
                )
                
                // 追加の切り替えエフェクト
                if (count % 3 == 0) {
                    player.world.spawnParticle(
                        Particle.ENCHANT,
                        player.location.add(0.0, 1.0, 0.0),
                        5,
                        0.5, 0.5, 0.5,
                        0.5
                    )
                }
                
                count++
            }
        }.runTaskTimer(plugin, 0L, 2L)
    }
    
    private fun showNoTargetEffect(player: Player) {
        player.world.spawnParticle(
            Particle.SMOKE,
            player.location.add(0.0, 1.5, 0.0),
            20,
            0.5, 0.5, 0.5,
            0.1
        )
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f)
    }
    
    private fun showDifferentWorldEffect(player: Player) {
        player.world.spawnParticle(
            Particle.PORTAL,
            player.location.add(0.0, 1.5, 0.0),
            30,
            0.5, 0.5, 0.5,
            0.1
        )
        player.playSound(player.location, Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 2.0f)
    }
    
    private fun getDirectionArrow(direction: Vector): String {
        // プレイヤーの向きに対する相対角度を計算
        val angle = Math.toDegrees(atan2(direction.x, direction.z))
        
        return when {
            angle >= -22.5 && angle < 22.5 -> "↑"
            angle >= 22.5 && angle < 67.5 -> "↗"
            angle >= 67.5 && angle < 112.5 -> "→"
            angle >= 112.5 && angle < 157.5 -> "↘"
            angle >= 157.5 || angle < -157.5 -> "↓"
            angle >= -157.5 && angle < -112.5 -> "↙"
            angle >= -112.5 && angle < -67.5 -> "←"
            angle >= -67.5 && angle < -22.5 -> "↖"
            else -> "?"
        }
    }
    
    fun showHunterHint(player: Player) {
        // ハンターに仮想コンパスの使い方を通知
        player.sendMessage(messageManager.getMessage(player, "compass.hint"))
        val actionBarMessage = messageManager.getMessage(player, "compass.actionbar-use")
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(actionBarMessage))
    }
    
    fun cleanup() {
        cooldowns.clear()
        targetIndex.clear()
    }
}