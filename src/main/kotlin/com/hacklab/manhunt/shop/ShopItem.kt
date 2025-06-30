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
enum class ShopCategory(val displayName: String, val icon: Material) {
    WEAPONS("武器", Material.IRON_SWORD),
    ARMOR("防具", Material.IRON_CHESTPLATE),
    TOOLS("ツール", Material.IRON_PICKAXE),
    CONSUMABLES("消耗品", Material.POTION),
    FOOD("食料", Material.COOKED_BEEF),
    SPECIAL("特殊", Material.NETHER_STAR)
}