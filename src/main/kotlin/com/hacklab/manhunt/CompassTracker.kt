package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CompassMeta
import org.bukkit.scheduler.BukkitRunnable

class CompassTracker(private val plugin: Main, private val gameManager: GameManager, private val configManager: ConfigManager) {
    
    private var trackingTask: BukkitRunnable? = null
    private val hunterCompasses = mutableMapOf<Player, ItemStack>()
    private var lastCompassUpdate = 0L
    
    fun startTracking() {
        stopTracking()
        trackingTask = object : BukkitRunnable() {
            override fun run() {
                // スロットリングで更新頻度を制限
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastCompassUpdate >= 1000) { // 1秒ごと
                    updateAllHunterCompasses()
                    lastCompassUpdate = currentTime
                }
            }
        }
        val checkInterval = (configManager.getCompassUpdateInterval() / 2).coerceAtLeast(1L)
        trackingTask?.runTaskTimer(plugin, 0L, checkInterval)
    }
    
    fun stopTracking() {
        try {
            trackingTask?.cancel()
        } catch (e: Exception) {
            plugin.logger.warning("Error stopping compass tracking task: ${e.message}")
        } finally {
            trackingTask = null
            hunterCompasses.clear() // キャッシュをクリア
            lastCompassUpdate = 0L
        }
    }
    
    private fun updateAllHunterCompasses() {
        val hunters = gameManager.getAllHunters()
        val runners = gameManager.getAllRunners()
        
        if (runners.isEmpty()) return
        
        for (hunter in hunters) {
            updateHunterCompass(hunter, runners)
        }
    }
    
    private fun updateHunterCompass(hunter: Player, runners: List<Player>) {
        val compass = getOrCreateCompass(hunter)
        if (compass != null) {
            val nearestRunner = findNearestRunner(hunter, runners)
            if (nearestRunner != null && nearestRunner.world != null && hunter.world != null && 
                nearestRunner.world == hunter.world) {
                setCompassTarget(compass, nearestRunner.location)
            }
        }
    }
    
    private fun getOrCreateCompass(hunter: Player): ItemStack? {
        // キャッシュされたコンパスをチェック
        hunterCompasses[hunter]?.let { cachedCompass ->
            // インベントリにまだあるか確認
            if (hunter.inventory.contains(cachedCompass)) {
                return cachedCompass
            } else {
                hunterCompasses.remove(hunter)
            }
        }
        
        // インベントリからコンパスを検索（高速化）
        val inventory = hunter.inventory
        val hotbar = inventory.storageContents.take(9) // ホットバーのみチェック
        for (item in hotbar) {
            if (item?.type == Material.COMPASS) {
                hunterCompasses[hunter] = item
                return item
            }
        }
        
        // 新しいコンパスを作成
        val compass = ItemStack(Material.COMPASS)
        val meta = compass.itemMeta
        if (meta !is CompassMeta) {
            plugin.logger.warning("Failed to create compass meta for ${hunter.name}")
            return null
        }
        
        meta.setDisplayName("§6追跡コンパス")
        meta.lore = listOf("§7最も近い逃げる人を指します")
        compass.itemMeta = meta
        
        // ホットバーの空きスロットに優先的に配置
        for (i in 0..8) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, compass)
                hunterCompasses[hunter] = compass
                return compass
            }
        }
        
        // ホットバーに空きがない場合は通常の追加
        val remainingItems = inventory.addItem(compass)
        return if (remainingItems.isEmpty()) {
            hunterCompasses[hunter] = compass
            compass
        } else {
            null
        }
    }
    
    private fun findNearestRunner(hunter: Player, runners: List<Player>): Player? {
        val hunterWorld = hunter.world ?: return null
        return runners
            .filter { runner -> 
                val runnerWorld = runner.world
                runnerWorld != null && runnerWorld == hunterWorld && !runner.isDead
            }
            .minByOrNull { runner ->
                try {
                    hunter.location.distance(runner.location)
                } catch (e: Exception) {
                    Double.MAX_VALUE
                }
            }
    }
    
    private fun setCompassTarget(compass: ItemStack, target: Location) {
        val meta = compass.itemMeta
        if (meta !is CompassMeta) return
        
        try {
            meta.lodestone = target
            meta.isLodestoneTracked = false // Allow tracking without lodestone block
            compass.itemMeta = meta
        } catch (e: Exception) {
            plugin.logger.warning("Failed to set compass target: ${e.message}")
        }
    }
    
    fun giveCompass(hunter: Player) {
        if (gameManager.getPlayerRole(hunter) != PlayerRole.HUNTER) {
            hunter.sendMessage("§c追う人のみがコンパスを使用できます！")
            return
        }
        
        val compass = ItemStack(Material.COMPASS)
        val meta = compass.itemMeta
        if (meta !is CompassMeta) {
            hunter.sendMessage("§cコンパスの作成に失敗しました。")
            return
        }
        
        meta.setDisplayName("§6追跡コンパス")
        meta.lore = listOf("§7最も近い逃げる人を指します")
        compass.itemMeta = meta
        
        val remainingItems = hunter.inventory.addItem(compass)
        if (remainingItems.isEmpty()) {
            hunter.sendMessage("§a追跡コンパスを受け取りました！")
        } else {
            hunter.sendMessage("§cインベントリに空きがありません！")
        }
    }
}