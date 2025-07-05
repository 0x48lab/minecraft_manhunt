package com.hacklab.manhunt

import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.inventory.meta.CompassMeta
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent

class CompassTracker(
    private val plugin: Main, 
    private val gameManager: GameManager, 
    private val configManager: ConfigManager,
    private val messageManager: MessageManager
) {
    
    private var trackingTask: BukkitRunnable? = null
    private var virtualCompass: VirtualCompass? = null
    
    fun startTracking() {
        stopTracking()
        
        // VirtualCompassを初期化
        virtualCompass = VirtualCompass(plugin, gameManager, configManager, messageManager)
        plugin.server.pluginManager.registerEvents(virtualCompass!!, plugin)
        virtualCompass?.startUpdateTask()
        
        // ハンターへのヒント表示タスク
        trackingTask = object : BukkitRunnable() {
            override fun run() {
                if (gameManager.getGameState() == GameState.RUNNING) {
                    showHunterHints()
                }
            }
        }
        
        // 30秒ごとにヒントを表示
        trackingTask?.runTaskTimer(plugin, 0L, 600L)
    }
    
    fun stopTracking() {
        try {
            trackingTask?.cancel()
        } catch (e: Exception) {
            plugin.logger.warning("Error stopping compass tracking task: ${e.message}")
        } finally {
            trackingTask = null
        }
        
        // VirtualCompassのクリーンアップ
        virtualCompass?.cleanup()
        virtualCompass = null
    }
    
    private fun showHunterHints() {
        val hunters = gameManager.getAllHunters()
        for (hunter in hunters) {
            if (hunter.isOnline) {
                // 一定確率でヒントを表示（スパム防止）
                if (Math.random() < 0.3) { // 30%の確率
                    val hintMessage = messageManager.getMessage(hunter, "compass.actionbar-use")
                    hunter.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(hintMessage))
                }
            }
        }
    }
    
    fun giveCompass(hunter: Player) {
        if (gameManager.getPlayerRole(hunter) != PlayerRole.HUNTER) {
            hunter.sendMessage(messageManager.getMessage(hunter, "compass.hunter-only"))
            return
        }
        
        if (gameManager.getGameState() != GameState.RUNNING) {
            hunter.sendMessage(messageManager.getMessage(hunter, "compass.game-only"))
            return
        }
        
        // 物理的なコンパスアイテムを提供（ターゲット切り替え用）
        givePhysicalCompass(hunter)
        
        // 仮想コンパスの使い方を説明
        hunter.sendMessage(messageManager.getMessage(hunter, "compass.activated"))
        hunter.sendMessage(messageManager.getMessage(hunter, "compass.usage"))
        hunter.sendMessage(messageManager.getMessage(hunter, "compass.slot-hint"))
        
        // ヒント表示
        virtualCompass?.showHunterHint(hunter)
        
        // タイトルでガイド表示
        val titleMessage = messageManager.getMessage(hunter, "compass.title-activated")
        val subtitleMessage = messageManager.getMessage(hunter, "compass.subtitle-activated")
        hunter.sendTitle(titleMessage, subtitleMessage, 10, 60, 10)
    }
    
    private fun givePhysicalCompass(hunter: Player) {
        // 既存のコンパス数をチェック
        val inventory = hunter.inventory
        var compassCount = 0
        
        val compassName = messageManager.getMessage("virtual-compass.name")
        for (item in inventory.contents) {
            if (item?.type == org.bukkit.Material.COMPASS) {
                val meta = item.itemMeta
                if (meta?.displayName == compassName) {
                    compassCount++
                }
            }
        }
        
        // 既に1個以上持っている場合は追加しない
        if (compassCount >= 1) {
            hunter.sendMessage(messageManager.getMessage(hunter, "compass.already-have"))
            return
        }
        
        // 新しいコンパスを作成
        val compass = org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS)
        val meta = compass.itemMeta as? CompassMeta
        if (meta != null) {
            meta.setDisplayName(messageManager.getMessage("virtual-compass.name"))
            meta.lore = messageManager.getMessageList("virtual-compass.lore")
            
            // 最寄りのランナーを初期ターゲットとして設定
            val nearestRunner = findNearestRunner(hunter)
            if (nearestRunner != null && nearestRunner.world == hunter.world) {
                meta.isLodestoneTracked = false
                meta.lodestone = nearestRunner.location
            }
            
            compass.itemMeta = meta
        }
        
        // ホットバーの最初のスロットに優先配置
        if (inventory.getItem(0) == null) {
            inventory.setItem(0, compass)
        } else {
            // 空きスロットに配置
            val remainingItems = inventory.addItem(compass)
            if (remainingItems.isNotEmpty()) {
                hunter.sendMessage(messageManager.getMessage(hunter, "compass.inventory-full"))
            }
        }
    }
    
    private fun findNearestRunner(hunter: Player): Player? {
        return gameManager.getAllRunners()
            .filter { it.isOnline && !it.isDead && it.world == hunter.world }
            .minByOrNull { it.location.distance(hunter.location) }
    }
    
    fun removePhysicalCompasses(player: Player) {
        // 既存の物理コンパスを削除（移行用）
        val inventory = player.inventory
        val compassName = messageManager.getMessage("virtual-compass.name")
        for (i in 0 until inventory.size) {
            val item = inventory.getItem(i)
            if (item?.type == org.bukkit.Material.COMPASS) {
                val meta = item.itemMeta
                if (meta?.displayName == compassName) {
                    inventory.setItem(i, null)
                }
            }
        }
    }
}