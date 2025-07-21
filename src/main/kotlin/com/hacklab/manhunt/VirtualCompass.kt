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
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import org.bukkit.inventory.meta.CompassMeta
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
    private val currentTargets = mutableMapOf<Player, Player>() // 現在のターゲット
    private var updateTask: BukkitRunnable? = null
    
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
        
        // コンパスを持っている場合のみ
        val itemInHand = player.inventory.itemInMainHand
        val isCompass = itemInHand.type == Material.COMPASS
        
        if (!isCompass) {
            return
        }
        
        // クールダウンチェック
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[player] ?: 0
        if (now - lastUse < cooldownTime) {
            val remaining = ((cooldownTime - (now - lastUse)) / 1000.0)
            player.sendMessage(messageManager.getMessage(player, "compass.cooldown", 
                "time" to String.format("%.1f", remaining)))
            return
        }
        
        // ターゲットを決定（循環選択）
        val targetRunner = getNextTarget(player)
        
        if (targetRunner == null) {
            player.sendMessage(messageManager.getMessage(player, "compass.no-target"))
            showNoTargetEffect(player)
            return
        }
        
        // 同じワールドかチェック
        if (player.world != targetRunner.world) {
            player.sendMessage(messageManager.getMessage(player, "compass.different-world", 
                "world" to targetRunner.world.name))
            showDifferentWorldEffect(player)
            cooldowns[player] = now
            return
        }
        
        // 方向と距離を計算
        val direction = calculateDirection(player, targetRunner)
        val distance = player.location.distance(targetRunner.location)
        
        // コンパスアイテムのメタデータを更新してターゲット位置を設定
        updateCompassTarget(player, targetRunner.location)
        currentTargets[player] = targetRunner
        
        // ビジュアルフィードバック（ターゲット切り替え表示）
        val allRunners = getAllValidRunners(player)
        val currentIndex = targetIndex[player] ?: 0
        showTargetSwitchEffect(player, direction, targetRunner, distance, currentIndex + 1, allRunners.size)
        
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
        
        // コンパスを持っているスロットに切り替えた場合
        val newItem = player.inventory.getItem(event.newSlot)
        if (newItem?.type == Material.COMPASS) {
            val meta = newItem.itemMeta
            val compassName = messageManager.getMessage("virtual-compass.name")
            if (meta?.displayName == compassName) {
                // ActionBarでヒントを表示
                val hintMessage = messageManager.getMessage(player, "compass.actionbar-hint")
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(hintMessage))
                
                // 初回ターゲット設定
                if (!currentTargets.containsKey(player)) {
                    val nearestRunner = findNearestRunner(player)
                    if (nearestRunner != null && nearestRunner.world == player.world) {
                        currentTargets[player] = nearestRunner
                        updateCompassTarget(player, nearestRunner.location)
                    }
                }
            }
        }
    }
    
    private fun findNearestRunner(hunter: Player): Player? {
        return getAllValidRunners(hunter)
            .minByOrNull { it.location.distance(hunter.location) }
    }
    
    private fun getAllValidRunners(hunter: Player): List<Player> {
        val runners = gameManager.getAllRunners()
            .filter { it.isOnline && !gameManager.isRunnerDead(it) && it.world == hunter.world }
        
        // デバッグ: ランナーの数をログ出力
        plugin.logger.info("VirtualCompass: Found ${runners.size} valid runners for hunter ${hunter.name}")
        runners.forEach { runner ->
            plugin.logger.info("  - Runner: ${runner.name} at ${runner.location.blockX}, ${runner.location.blockY}, ${runner.location.blockZ}")
        }
        
        return runners
    }
    
    private fun getNextTarget(hunter: Player): Player? {
        val runners = getAllValidRunners(hunter)
        if (runners.isEmpty()) return null
        
        val currentIndex = targetIndex[hunter] ?: 0
        val nextIndex = (currentIndex + 1) % runners.size
        targetIndex[hunter] = nextIndex
        
        return runners[nextIndex]
    }
    
    private fun updateCompassTarget(player: Player, targetLocation: Location) {
        // デバッグ: ターゲット位置をログ出力
        plugin.logger.info("VirtualCompass: Setting compass target for ${player.name} to ${targetLocation.blockX}, ${targetLocation.blockY}, ${targetLocation.blockZ}")
        
        // プレイヤーのコンパスターゲットを直接設定
        player.compassTarget = targetLocation
        
        // 手持ちのコンパスアイテムも更新
        val compass = player.inventory.itemInMainHand
        if (compass.type == Material.COMPASS) {
            val meta = compass.itemMeta as? CompassMeta
            if (meta != null) {
                meta.isLodestoneTracked = false
                meta.lodestone = targetLocation
                compass.itemMeta = meta
            }
        }
        
        // インベントリ内の全てのコンパスを更新
        player.inventory.contents.forEach { item ->
            if (item?.type == Material.COMPASS) {
                val meta = item.itemMeta as? CompassMeta
                if (meta != null) {
                    meta.isLodestoneTracked = false
                    meta.lodestone = targetLocation
                    item.itemMeta = meta
                }
            }
        }
        
        // インベントリを更新して変更を反映
        player.updateInventory()
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
            "player" to target.name, "distance" to formattedDistance))
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
            "$formattedDistance §7| ${getDirectionArrow(direction)} §8| ${messageManager.getMessage("virtual-compass.target-switch")}",
            10, 50, 10
        )
        
        // 特別なパーティクル表示（青色）
        showTargetSwitchParticles(hunter, direction)
        
        // 切り替えサウンド
        hunter.playSound(hunter.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f)
        
        // チャットメッセージ
        hunter.sendMessage(messageManager.getMessage(hunter, "compass.target-switched", 
            "index" to currentIndex, "total" to totalTargets, "player" to target.name, "distance" to formattedDistance))
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
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val playerRole = gameManager.getPlayerRole(player)
        
        // 追跡コンパスをドロップから削除（ハンター・ランナー共通）
        val compassName = messageManager.getMessage("virtual-compass.name")
        val iterator = event.drops.iterator()
        var savedCompass: org.bukkit.inventory.ItemStack? = null
        
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.type == Material.COMPASS) {
                val meta = item.itemMeta
                if (meta?.displayName == compassName) {
                    if (playerRole == PlayerRole.HUNTER) {
                        savedCompass = item.clone()
                    }
                    iterator.remove() // ドロップから削除（ハンター・ランナー共通）
                }
            }
        }
        
        // ハンターの場合のみリスポン後にコンパスを復元
        if (playerRole == PlayerRole.HUNTER && savedCompass != null) {
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                if (player.isOnline && gameManager.getPlayerRole(player) == PlayerRole.HUNTER) {
                    // ホットバーの最初のスロットに復元
                    player.inventory.setItem(0, savedCompass)
                    player.sendMessage(messageManager.getMessage(player, "virtual-compass.compass-restored"))
                }
            }, 1L) // 1tick後に実行
        }
    }
    
    @EventHandler
    fun onPlayerPickupItem(event: PlayerPickupItemEvent) {
        val player = event.player
        val item = event.item.itemStack
        
        // 追跡コンパスの拾得を制限
        if (item.type == Material.COMPASS) {
            val meta = item.itemMeta
            val compassName = messageManager.getMessage("virtual-compass.name")
            if (meta?.displayName == compassName) {
                // ハンター以外は拾えない
                if (gameManager.getPlayerRole(player) != PlayerRole.HUNTER) {
                    event.isCancelled = true
                    return
                }
                
                // 既にコンパスを持っているハンターは拾えない
                val inventory = player.inventory
                var compassCount = 0
                
                for (invItem in inventory.contents) {
                    if (invItem?.type == Material.COMPASS) {
                        val invMeta = invItem.itemMeta
                        if (invMeta?.displayName == compassName) {
                            compassCount++
                        }
                    }
                }
                
                if (compassCount >= 1) {
                    event.isCancelled = true
                    player.sendMessage(messageManager.getMessage(player, "virtual-compass.already-have-pickup"))
                }
            }
        }
    }
    
    fun startUpdateTask() {
        stopUpdateTask()
        
        updateTask = object : BukkitRunnable() {
            override fun run() {
                if (gameManager.getGameState() != GameState.RUNNING) {
                    return
                }
                
                // 全ハンターのコンパスを更新
                for ((hunter, target) in currentTargets) {
                    if (!hunter.isOnline || hunter.isDead || !target.isOnline || target.isDead) {
                        continue
                    }
                    
                    // 異なるワールドの場合はスキップ
                    if (hunter.world != target.world) {
                        continue
                    }
                    
                    // コンパスを持っているか確認して更新
                    updateAllCompassesInInventory(hunter, target.location)
                }
            }
        }
        
        // 1秒ごとに更新
        updateTask?.runTaskTimer(plugin, 0L, 20L)
    }
    
    fun stopUpdateTask() {
        updateTask?.cancel()
        updateTask = null
    }
    
    private fun updateAllCompassesInInventory(player: Player, targetLocation: Location) {
        // プレイヤーのコンパスターゲットを直接設定（これが重要）
        player.compassTarget = targetLocation
        
        val inventory = player.inventory
        val compassName = messageManager.getMessage("virtual-compass.name")
        
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i) ?: continue
            if (item.type != Material.COMPASS) continue
            
            val meta = item.itemMeta as? CompassMeta ?: continue
            if (meta.displayName == compassName) {
                meta.isLodestoneTracked = false
                meta.lodestone = targetLocation
                item.itemMeta = meta
            }
        }
        
        // 手に持っているアイテムも確認
        val mainHand = player.inventory.itemInMainHand
        if (mainHand.type == Material.COMPASS) {
            val meta = mainHand.itemMeta as? CompassMeta
            if (meta?.displayName == compassName) {
                meta.isLodestoneTracked = false
                meta.lodestone = targetLocation
                mainHand.itemMeta = meta
            }
        }
        
        // インベントリを更新
        player.updateInventory()
    }
    
    fun cleanup() {
        stopUpdateTask()
        cooldowns.clear()
        targetIndex.clear()
        currentTargets.clear()
    }
    
    fun setInitialTarget(hunter: Player, target: Player) {
        currentTargets[hunter] = target
        targetIndex[hunter] = 0
        plugin.logger.info("VirtualCompass: Set initial target for ${hunter.name} to ${target.name}")
    }
}
