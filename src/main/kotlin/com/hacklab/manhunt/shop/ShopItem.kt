package com.hacklab.manhunt.shop

import com.hacklab.manhunt.PlayerRole
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * ショップで販売するアイテムのデータクラス
 */
data class ShopItem(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val price: Int,
    val itemStack: ItemStack,
    val category: ShopCategory,
    val allowedRoles: Set<PlayerRole>,
    val maxPurchases: Int = -1,  // -1 = 無制限
    val cooldown: Int = 0,       // 秒単位のクールダウン
    val setItems: List<String>? = null  // セット商品の場合のマテリアル名リスト
) {
    // shop.ymlが完備されているため、getDefaultItems()は削除
}

/**
 * ショップカテゴリー
 */
enum class ShopCategory(val messageKey: String, val icon: Material) {
    WEAPONS("shop-extended.categories.weapons", Material.IRON_SWORD),
    ARMOR("shop-extended.categories.armor", Material.IRON_CHESTPLATE),
    TOOLS("shop-extended.categories.tools", Material.IRON_PICKAXE),
    CONSUMABLES("shop-extended.categories.consumables", Material.POTION),
    FOOD("shop-extended.categories.food", Material.COOKED_BEEF),
    SPECIAL("shop-extended.categories.special", Material.NETHER_STAR)
}