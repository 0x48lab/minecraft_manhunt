package com.hacklab.manhunt.shop

import com.hacklab.manhunt.Main
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
    private val shopItems = mutableListOf<ShopItem>()
    private val purchaseHistory = mutableMapOf<UUID, MutableMap<String, PurchaseRecord>>()
    private val openShops = mutableMapOf<UUID, ShopCategory?>()
    private val shopConfigManager = ShopConfigManager(plugin)
    private var categories = mapOf<ShopCategory, ShopConfigManager.CategoryConfig>()
    
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
                plugin.logger.info("shop.yml から ${configItems.size} 個のアイテムを読み込みました")
            } else {
                // フォールバック: ハードコーディングされたアイテムを使用
                plugin.logger.warning("shop.yml からアイテムを読み込めませんでした。デフォルトアイテムを使用します")
                shopItems.addAll(ShopItem.getDefaultItems())
            }
            
            // カテゴリ設定を読み込み
            categories = shopConfigManager.loadCategories()
            
        } catch (e: Exception) {
            plugin.logger.severe("ショップアイテムの読み込みでエラー: ${e.message}")
            // エラー時はデフォルトアイテムを使用
            shopItems.addAll(ShopItem.getDefaultItems())
        }
    }
    
    /**
     * ショップ設定をリロード
     */
    fun reloadShopConfig() {
        try {
            shopConfigManager.reloadShopConfig()
            loadShopItems()
            plugin.logger.info("ショップ設定をリロードしました")
        } catch (e: Exception) {
            plugin.logger.severe("ショップ設定のリロードでエラー: ${e.message}")
        }
    }
    
    /**
     * ショップを開く
     */
    fun openShop(player: Player, category: ShopCategory? = null) {
        val role = plugin.getGameManager().getPlayerRole(player)
        if (role == null || role == PlayerRole.SPECTATOR) {
            player.sendMessage("§c観戦者はショップを使用できません。")
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
        val inventory = Bukkit.createInventory(null, 27, "§6§lショップ - カテゴリ選択")
        
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
                val displayName = categoryConfig?.displayName ?: category.displayName
                val icon = categoryConfig?.icon ?: category.icon
                
                val item = ItemStack(icon)
                val meta = item.itemMeta!!
                val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
                meta.setDisplayName("§e${displayName}")
                meta.lore = listOf(
                    "§7クリックして${displayName}を見る",
                    "",
                    "§b所持金: §f${economyManager.getBalance(player)}${unit}"
                )
                item.itemMeta = meta
                inventory.setItem(startSlot + index, item)
            }
        }
        
        // 閉じるボタン
        inventory.setItem(22, createCloseButton())
        
        return inventory
    }
    
    /**
     * カテゴリ別ショップを作成
     */
    private fun createCategoryShop(player: Player, role: PlayerRole, category: ShopCategory): Inventory {
        val items = getItemsForCategory(role, category)
        val size = ((items.size - 1) / 9 + 2) * 9 // 最低2行、アイテム数に応じて拡張
        val inventory = Bukkit.createInventory(null, size.coerceIn(18, 54), "§6§lショップ - ${category.displayName}")
        
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
        
        meta.setDisplayName(shopItem.displayName)
        
        val lore = mutableListOf<String>()
        lore.addAll(shopItem.description)
        lore.add("")
        val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
        lore.add("§6価格: §f${shopItem.price}${unit}")
        
        if (shopItem.maxPurchases > 0) {
            lore.add("§7購入制限: §f${purchaseCount}/${shopItem.maxPurchases}")
        }
        
        if (shopItem.cooldown > 0 && purchaseRecord != null) {
            val remainingCooldown = getRemainingCooldown(purchaseRecord, shopItem.cooldown)
            if (remainingCooldown > 0) {
                lore.add("§cクールダウン: ${remainingCooldown}秒")
            }
        }
        
        lore.add("")
        when {
            !economyManager.hasEnoughMoney(player, shopItem.price) -> {
                lore.add("§c✗ ${unit}が不足しています")
            }
            !canPurchase -> {
                lore.add("§c✗ 購入できません")
            }
            else -> {
                lore.add("§a✓ クリックして購入")
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
            player.sendMessage("§cこのアイテムは購入できません。")
            return false
        }
        
        // 残高チェック
        if (!economyManager.hasEnoughMoney(player, shopItem.price)) {
            val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
            player.sendMessage("§c${unit}が不足しています。(必要: ${shopItem.price}${unit})")
            return false
        }
        
        // 支払い処理
        if (!economyManager.removeMoney(player, shopItem.price)) {
            player.sendMessage("§c購入処理中にエラーが発生しました。")
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
                    plugin.logger.warning("無効なマテリアル: $materialName (セットアイテム: ${shopItem.id})")
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
        player.sendMessage("§a${shopItem.displayName}§aを購入しました！ (-${shopItem.price}${unit})")
        player.sendMessage("§7残高: §f${economyManager.getBalance(player)}${unit}")
        
        // 効果音
        player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        
        plugin.logger.info("${player.name} が ${shopItem.displayName} を購入 (${shopItem.price}${unit})")
        
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
        meta.setDisplayName("§c戻る")
        meta.lore = listOf("§7カテゴリ選択に戻る")
        item.itemMeta = meta
        return item
    }
    
    private fun createCloseButton(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta!!
        meta.setDisplayName("§c閉じる")
        item.itemMeta = meta
        return item
    }
    
    private fun createBalanceItem(player: Player): ItemStack {
        val item = ItemStack(Material.GOLD_NUGGET)
        val meta = item.itemMeta!!
        val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
        meta.setDisplayName("§6所持金")
        meta.lore = listOf("§f${economyManager.getBalance(player)}${unit}")
        item.itemMeta = meta
        return item
    }
}

/**
 * 購入記録
 */
data class PurchaseRecord(
    var count: Int,
    var lastPurchase: Long
)