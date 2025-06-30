package com.hacklab.manhunt.shop

import com.hacklab.manhunt.Main
import com.hacklab.manhunt.economy.CurrencyTracker
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.inventory.ItemStack

/**
 * ショップと通貨システムのイベントリスナー
 */
class ShopListener(
    private val plugin: Main,
    private val shopManager: ShopManager,
    private val currencyTracker: CurrencyTracker
) : Listener {
    
    /**
     * インベントリクリックイベント
     */
    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        
        // ショップインベントリかチェック
        val title = event.view.title
        val messageManager = plugin.getMessageManager()
        val categoryTitle = messageManager.getMessage("shop-extended.menu.category-title")
        
        // タイトルがショップ関連かチェック（より柔軟に）
        val isShopInventory = title.contains(categoryTitle) || 
                            title.contains("Shop") || 
                            title.contains("ショップ")
        
        if (!isShopInventory) return
        
        event.isCancelled = true
        
        // カテゴリ選択メニューの場合
        if (title.contains(categoryTitle) || title.contains("Category") || title.contains("カテゴリ")) {
            handleCategorySelection(player, clickedItem)
            return
        }
        
        // アイテムショップの場合
        handleItemPurchase(player, clickedItem)
    }
    
    /**
     * カテゴリ選択の処理
     */
    private fun handleCategorySelection(player: Player, clickedItem: ItemStack) {
        when (clickedItem.type) {
            Material.IRON_SWORD -> shopManager.openShop(player, ShopCategory.WEAPONS)
            Material.IRON_CHESTPLATE -> shopManager.openShop(player, ShopCategory.ARMOR)
            Material.IRON_PICKAXE -> shopManager.openShop(player, ShopCategory.TOOLS)
            Material.POTION -> shopManager.openShop(player, ShopCategory.CONSUMABLES)
            Material.COOKED_BEEF -> shopManager.openShop(player, ShopCategory.FOOD)
            Material.NETHER_STAR -> shopManager.openShop(player, ShopCategory.SPECIAL)
            Material.BARRIER -> player.closeInventory()
            else -> {}
        }
    }
    
    /**
     * アイテム購入の処理
     */
    private fun handleItemPurchase(player: Player, clickedItem: ItemStack) {
        val displayName = clickedItem.itemMeta?.displayName ?: return
        
        when (clickedItem.type) {
            Material.ARROW -> {
                // 戻るボタン
                shopManager.openShop(player)
            }
            Material.BARRIER -> {
                // 閉じるボタン
                player.closeInventory()
            }
            Material.GOLD_NUGGET -> {
                // 残高表示（何もしない）
            }
            else -> {
                // ショップアイテムの購入
                val shopItem = shopManager.getShopItemByDisplayName(displayName)
                if (shopItem != null) {
                    if (shopManager.purchaseItem(player, shopItem)) {
                        // 購入成功後、ショップを更新
                        val category = shopManager.getOpenCategory(player)
                        if (category != null) {
                            shopManager.openShop(player, category)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * インベントリを閉じた時
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        shopManager.onCloseShop(player)
    }
    
    /**
     * ダメージイベント（通貨獲得用）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (event.entity !is Player || event.damager !is Player) return
        
        val victim = event.entity as Player
        val attacker = event.damager as Player
        val damage = event.finalDamage
        
        currencyTracker.onDamageDealt(attacker, victim, damage)
        
        // ゲーム統計にダメージを記録
        try {
            plugin.getGameManager().recordDamage(attacker, victim, damage)
        } catch (e: Exception) {
            plugin.logger.warning("ダメージ統計記録でエラー: ${e.message}")
        }
    }
    
    /**
     * プレイヤー死亡イベント
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer
        
        if (killer != null) {
            currencyTracker.onPlayerKill(killer, victim)
            
            // ゲーム統計にキルを記録
            try {
                plugin.getGameManager().recordKill(killer, victim)
            } catch (e: Exception) {
                plugin.logger.warning("キル統計記録でエラー: ${e.message}")
            }
        }
    }
    
    /**
     * ワールド変更イベント
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        val toWorld = player.world
        
        currencyTracker.onDimensionChange(player, toWorld.environment)
    }
    
    /**
     * ポータルイベント
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPortal(event: PlayerPortalEvent) {
        val player = event.player
        val to = event.to ?: return
        
        currencyTracker.onDimensionChange(player, to.world!!.environment)
    }
    
    /**
     * アイテム拾得イベント
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (event.entity.type != EntityType.PLAYER) return
        
        val player = event.entity as Player
        val item = event.item.itemStack
        
        currencyTracker.onItemCollected(player, item.type, item.amount)
    }
}