package com.hacklab.manhunt.shop

import com.hacklab.manhunt.Main
import com.hacklab.manhunt.MessageManager
import com.hacklab.manhunt.PlayerRole
import com.hacklab.manhunt.economy.EconomyManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * ショップシステムの管理クラス
 */
class ShopManager(
    private val plugin: Main,
    private val economyManager: EconomyManager
) {
    private val messageManager: MessageManager
        get() = plugin.getMessageManager()
    private val shopItems = mutableListOf<ShopItem>()
    private val purchaseHistory = mutableMapOf<UUID, MutableMap<String, PurchaseRecord>>()
    private val openShops = mutableMapOf<UUID, ShopCategory?>()
    private val shopConfigManager = ShopConfigManager(plugin)
    private var categories = mapOf<ShopCategory, ShopConfigManager.CategoryConfig>()
    private val shopItemPreferences = mutableMapOf<UUID, Boolean>() // true = show shop item in inventory
    
    init {
        loadShopItems()
    }
    
    /**
     * ショップアイテムを読み込み（設定ファイル優先）
     */
    private fun loadShopItems() {
        shopItems.clear()
        
        try {
            // 設定ファイルからアイテムを読み込み
            val configItems = shopConfigManager.loadShopItems()
            if (configItems.isNotEmpty()) {
                shopItems.addAll(configItems)
                plugin.logger.info("Loaded ${configItems.size} items from shop.yml")
            } else {
                plugin.logger.severe("Failed to load items from shop.yml - no items loaded!")
                // shop.ymlが完備されているため、フォールバックは削除
            }
            
            // カテゴリ設定を読み込み
            categories = shopConfigManager.loadCategories()
            
        } catch (e: Exception) {
            plugin.logger.severe("Error loading shop items: ${e.message}")
            e.printStackTrace()
            // shop.ymlが完備されているため、フォールバックは削除
        }
    }
    
    /**
     * ショップ設定をリロード
     */
    fun reloadShopConfig() {
        try {
            shopConfigManager.reloadShopConfig()
            loadShopItems()
            plugin.logger.info("Shop configuration reloaded")
        } catch (e: Exception) {
            plugin.logger.severe("Error reloading shop configuration: ${e.message}")
        }
    }
    
    /**
     * ショップを開く
     */
    fun openShop(player: Player, category: ShopCategory? = null) {
        val role = plugin.getGameManager().getPlayerRole(player)
        if (role == null || role == PlayerRole.SPECTATOR) {
            player.sendMessage(messageManager.getMessage(player, "shop.spectator-denied"))
            return
        }
        
        val inventory = if (category == null) {
            createCategoryMenu(player, role)
        } else {
            createCategoryShop(player, role, category)
        }
        
        openShops[player.uniqueId] = category
        player.openInventory(inventory)
    }
    
    /**
     * カテゴリ選択メニューを作成
     */
    private fun createCategoryMenu(player: Player, role: PlayerRole): Inventory {
        val inventory = Bukkit.createInventory(null, 27, messageManager.getMessage(player, "shop-extended.menu.category-title"))
        
        // 背景をグレーのガラスで埋める
        for (i in 0 until 27) {
            inventory.setItem(i, createFillerItem())
        }
        
        // カテゴリアイコンを配置
        val allCategories = ShopCategory.values()
        val startSlot = 10
        allCategories.forEachIndexed { index, category ->
            if (hasItemsInCategory(role, category)) {
                // 設定ファイルからカテゴリ情報を取得、なければデフォルト値
                val categoryConfig = categories[category]
                val displayName = categoryConfig?.displayName ?: messageManager.getMessage(category.messageKey)
                val icon = categoryConfig?.icon ?: category.icon
                
                val item = ItemStack(icon)
                val meta = item.itemMeta!!
                val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
                meta.setDisplayName("§e${displayName}")
                meta.lore = listOf(
                    messageManager.getMessage(player, "shop-extended.menu.category-click", "category" to displayName),
                    "",
                    messageManager.getMessage(player, "shop-extended.menu.balance-display", "balance" to economyManager.getBalance(player), "unit" to unit)
                )
                item.itemMeta = meta
                inventory.setItem(startSlot + index, item)
            }
        }
        
        // 操作ボタン
        inventory.setItem(18, createShopItemToggleButton(player))  // 左下にトグルボタン
        inventory.setItem(22, createCloseButton())  // 下中央に閉じるボタン
        
        return inventory
    }
    
    /**
     * カテゴリ別ショップを作成
     */
    private fun createCategoryShop(player: Player, role: PlayerRole, category: ShopCategory): Inventory {
        val items = getItemsForCategory(role, category)
        val size = ((items.size - 1) / 9 + 2) * 9 // 最低2行、アイテム数に応じて拡張
        val categoryDisplayName = categories[category]?.displayName ?: messageManager.getMessage(category.messageKey)
        val title = messageManager.getMessage(player, "shop-extended.menu.shop-title", "category" to categoryDisplayName)
        val inventory = Bukkit.createInventory(null, size.coerceIn(18, 54), title)
        
        // アイテムを配置
        items.forEachIndexed { index, shopItem ->
            val displayItem = createShopDisplayItem(player, shopItem)
            inventory.setItem(index, displayItem)
        }
        
        // 最下段に操作ボタンを配置
        val lastRow = size - 9
        inventory.setItem(lastRow, createBackButton())
        inventory.setItem(lastRow + 4, createBalanceItem(player))
        inventory.setItem(lastRow + 8, createCloseButton())
        
        return inventory
    }
    
    /**
     * ショップ表示用アイテムを作成
     */
    private fun createShopDisplayItem(player: Player, shopItem: ShopItem): ItemStack {
        val item = shopItem.itemStack.clone()
        val meta = item.itemMeta!!
        
        // 購入履歴を確認
        val purchaseRecord = purchaseHistory[player.uniqueId]?.get(shopItem.id)
        val purchaseCount = purchaseRecord?.count ?: 0
        val canPurchase = canPlayerPurchase(player, shopItem)
        
        // プレイヤーの言語に応じた表示名を取得
        val displayName = messageManager.getMessage(player, "shop-extended.items.${shopItem.id}.name").let {
            if (it.startsWith("§c[Missing message:")) shopItem.displayName else it
        }
        meta.setDisplayName(displayName)
        
        // プレイヤーの言語に応じた説明を取得
        val description = messageManager.getMessageList(player, "shop-extended.items.${shopItem.id}.description").let {
            if (it.isEmpty()) shopItem.description else it
        }
        
        val lore = mutableListOf<String>()
        lore.addAll(description)
        lore.add("")
        val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
        lore.add(messageManager.getMessage(player, "shop-extended.item.price-format", "price" to shopItem.price, "unit" to unit))
        
        if (shopItem.maxPurchases > 0) {
            lore.add(messageManager.getMessage(player, "shop-extended.item.purchase-limit", "current" to purchaseCount, "max" to shopItem.maxPurchases))
        }
        
        if (shopItem.cooldown > 0 && purchaseRecord != null) {
            val remainingCooldown = getRemainingCooldown(purchaseRecord, shopItem.cooldown)
            if (remainingCooldown > 0) {
                lore.add(messageManager.getMessage(player, "shop-extended.item.cooldown", "time" to remainingCooldown))
            }
        }
        
        lore.add("")
        when {
            !economyManager.hasEnoughMoney(player, shopItem.price) -> {
                lore.add(messageManager.getMessage(player, "shop-extended.item.insufficient-funds", "unit" to unit))
            }
            !canPurchase -> {
                lore.add(messageManager.getMessage(player, "shop-extended.item.cannot-purchase"))
            }
            else -> {
                lore.add(messageManager.getMessage(player, "shop-extended.item.click-to-purchase"))
            }
        }
        
        meta.lore = lore
        item.itemMeta = meta
        
        return item
    }
    
    /**
     * アイテムの購入処理
     */
    fun purchaseItem(player: Player, shopItem: ShopItem): Boolean {
        // 購入可能かチェック
        if (!canPlayerPurchase(player, shopItem)) {
            player.sendMessage(messageManager.getMessage(player, "shop.cannot-purchase"))
            return false
        }
        
        // 残高チェック
        if (!economyManager.hasEnoughMoney(player, shopItem.price)) {
            val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
            player.sendMessage(messageManager.getMessage(player, "shop.insufficient-funds", "unit" to unit, "price" to shopItem.price))
            return false
        }
        
        // 支払い処理
        if (!economyManager.removeMoney(player, shopItem.price)) {
            player.sendMessage(messageManager.getMessage(player, "shop.purchase-error"))
            return false
        }
        
        // アイテムを付与（セット商品の場合は複数アイテム）
        if (shopItem.setItems != null && shopItem.setItems.isNotEmpty()) {
            // セット商品の場合
            for (materialName in shopItem.setItems) {
                try {
                    val material = Material.valueOf(materialName.uppercase())
                    player.inventory.addItem(ItemStack(material))
                } catch (e: IllegalArgumentException) {
                    plugin.logger.warning("Invalid material: $materialName (set item: ${shopItem.id})")
                }
            }
        } else {
            // 通常商品の場合
            player.inventory.addItem(shopItem.itemStack.clone())
        }
        
        // 購入履歴を更新
        val playerHistory = purchaseHistory.getOrPut(player.uniqueId) { mutableMapOf() }
        val record = playerHistory.getOrPut(shopItem.id) { PurchaseRecord(0, 0L) }
        record.count++
        record.lastPurchase = System.currentTimeMillis()
        
        // 成功メッセージ
        val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
        player.sendMessage(messageManager.getMessage(player, "shop.purchase-success", "item" to shopItem.displayName, "price" to shopItem.price, "unit" to unit))
        player.sendMessage(messageManager.getMessage(player, "shop.balance-remaining", "balance" to economyManager.getBalance(player), "unit" to unit))
        
        // 効果音
        player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        
        plugin.logger.info("${player.name} purchased ${shopItem.displayName} (${shopItem.price}${unit})")
        
        return true
    }
    
    /**
     * プレイヤーがアイテムを購入できるかチェック
     */
    private fun canPlayerPurchase(player: Player, shopItem: ShopItem): Boolean {
        val role = plugin.getGameManager().getPlayerRole(player) ?: return false
        
        // 役割チェック
        if (!shopItem.allowedRoles.contains(role)) {
            return false
        }
        
        // 購入制限チェック
        if (shopItem.maxPurchases > 0) {
            val purchaseCount = purchaseHistory[player.uniqueId]?.get(shopItem.id)?.count ?: 0
            if (purchaseCount >= shopItem.maxPurchases) {
                return false
            }
        }
        
        // クールダウンチェック
        if (shopItem.cooldown > 0) {
            val record = purchaseHistory[player.uniqueId]?.get(shopItem.id)
            if (record != null && getRemainingCooldown(record, shopItem.cooldown) > 0) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * 残りクールダウン時間を取得
     */
    private fun getRemainingCooldown(record: PurchaseRecord, cooldownSeconds: Int): Int {
        val elapsedSeconds = (System.currentTimeMillis() - record.lastPurchase) / 1000
        return (cooldownSeconds - elapsedSeconds).toInt().coerceAtLeast(0)
    }
    
    /**
     * 役割に応じたカテゴリのアイテムがあるかチェック
     */
    private fun hasItemsInCategory(role: PlayerRole, category: ShopCategory): Boolean {
        return shopItems.any { it.category == category && it.allowedRoles.contains(role) }
    }
    
    /**
     * 役割とカテゴリに応じたアイテムを取得
     */
    private fun getItemsForCategory(role: PlayerRole, category: ShopCategory): List<ShopItem> {
        return shopItems.filter { it.category == category && it.allowedRoles.contains(role) }
    }
    
    /**
     * ショップを閉じた時の処理
     */
    fun onCloseShop(player: Player) {
        openShops.remove(player.uniqueId)
    }
    
    /**
     * 現在開いているショップのカテゴリを取得
     */
    fun getOpenCategory(player: Player): ShopCategory? {
        return openShops[player.uniqueId]
    }
    
    /**
     * アイテムIDからショップアイテムを取得
     */
    fun getShopItemByDisplayName(displayName: String): ShopItem? {
        return shopItems.find { it.displayName == displayName }
    }
    
    /**
     * ゲーム開始時のリセット
     */
    fun resetAllPurchases() {
        purchaseHistory.clear()
        openShops.clear()
    }
    
    // ユーティリティメソッド
    private fun createFillerItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta!!
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }
    
    private fun createBackButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.setDisplayName(messageManager.getMessage("shop-extended.buttons.back"))
        meta.lore = listOf(messageManager.getMessage("shop-extended.buttons.back-description"))
        item.itemMeta = meta
        return item
    }
    
    private fun createCloseButton(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta!!
        meta.setDisplayName(messageManager.getMessage("shop-extended.buttons.close"))
        item.itemMeta = meta
        return item
    }
    
    private fun createBalanceItem(player: Player): ItemStack {
        val item = ItemStack(Material.GOLD_NUGGET)
        val meta = item.itemMeta!!
        val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
        meta.setDisplayName(messageManager.getMessage(player, "shop-extended.buttons.balance-title"))
        meta.lore = listOf(messageManager.getMessage(player, "shop-extended.buttons.balance-amount", "balance" to economyManager.getBalance(player), "unit" to unit))
        item.itemMeta = meta
        return item
    }
    
    /**
     * ショップアイテム表示設定ボタンを作成
     */
    private fun createShopItemToggleButton(player: Player): ItemStack {
        val showShopItem = getShowShopItemPreference(player)
        val item = ItemStack(if (showShopItem) Material.LIME_DYE else Material.GRAY_DYE)
        val meta = item.itemMeta!!
        
        meta.setDisplayName(messageManager.getMessage(player, "shop-extended.buttons.toggle-item-title"))
        meta.lore = listOf(
            messageManager.getMessage(player, "shop-extended.buttons.toggle-item-current", 
                "status" to if (showShopItem) 
                    messageManager.getMessage(player, "shop-extended.buttons.toggle-item-enabled") 
                else 
                    messageManager.getMessage(player, "shop-extended.buttons.toggle-item-disabled")),
            "",
            messageManager.getMessage(player, "shop-extended.buttons.toggle-item-click")
        )
        
        item.itemMeta = meta
        return item
    }
    
    /**
     * プレイヤーのショップアイテム表示設定を取得
     */
    fun getShowShopItemPreference(player: Player): Boolean {
        return shopItemPreferences.getOrDefault(player.uniqueId, true)
    }
    
    /**
     * プレイヤーのショップアイテム表示設定を変更
     */
    fun toggleShopItemPreference(player: Player): Boolean {
        val newValue = !getShowShopItemPreference(player)
        shopItemPreferences[player.uniqueId] = newValue
        
        // 設定を変更したらすぐに反映
        if (newValue) {
            // ショップアイテムを付与
            if (plugin.getGameManager().getGameState() == com.hacklab.manhunt.GameState.RUNNING) {
                val role = plugin.getGameManager().getPlayerRole(player)
                if (role != null && role != PlayerRole.SPECTATOR) {
                    giveShopItemToPlayer(player)
                }
            }
        } else {
            // ショップアイテムを削除
            removeShopItemFromPlayer(player)
        }
        
        return newValue
    }
    
    /**
     * プレイヤーにショップアイテムを付与
     */
    private fun giveShopItemToPlayer(player: Player) {
        // 既に持っているかチェック
        if (player.inventory.contents.any { it != null && isShopItem(it) }) {
            return
        }
        
        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta!!
        meta.setDisplayName(messageManager.getMessage(player, "item.shop.name"))
        meta.lore = listOf(
            messageManager.getMessage(player, "item.shop.lore1"),
            messageManager.getMessage(player, "item.shop.lore2")
        )
        // アイテムを光らせる
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        
        // スロット7に配置（コンパスはスロット8）
        player.inventory.setItem(7, item)
    }
    
    /**
     * プレイヤーからショップアイテムを削除
     */
    private fun removeShopItemFromPlayer(player: Player) {
        player.inventory.contents.forEachIndexed { index, itemStack ->
            if (itemStack != null && isShopItem(itemStack)) {
                player.inventory.setItem(index, null)
            }
        }
    }
    
    /**
     * アイテムがショップアイテムかチェック
     */
    private fun isShopItem(item: ItemStack): Boolean {
        if (item.type != Material.EMERALD) return false
        val meta = item.itemMeta ?: return false
        val displayName = meta.displayName ?: return false
        return displayName.contains("ショップ") || displayName.contains("Shop") || displayName.contains("商店") || displayName.contains("Store")
    }
}

/**
 * 購入記録
 */
data class PurchaseRecord(
    var count: Int,
    var lastPurchase: Long
)