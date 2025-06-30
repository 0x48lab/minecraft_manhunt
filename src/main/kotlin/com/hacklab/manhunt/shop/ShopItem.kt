package com.hacklab.manhunt.shop

import com.hacklab.manhunt.PlayerRole
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

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
    companion object {
        // プリセットアイテムの定義
        fun getDefaultItems(): List<ShopItem> {
            return listOf(
                // 武器 - 剣
                ShopItem(
                    id = "iron_sword",
                    displayName = "§f鉄の剣",
                    description = listOf("§7実用的な鉄の剣", "§7攻撃力: 6", "§7高い耐久性"),
                    price = 800,
                    itemStack = ItemStack(Material.IRON_SWORD),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "golden_sword",
                    displayName = "§6金の剣",
                    description = listOf("§7軽量で素早い剣", "§7攻撃力: 4", "§c耐久度が低い"),
                    price = 300,
                    itemStack = ItemStack(Material.GOLDEN_SWORD),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "diamond_sword",
                    displayName = "§bダイヤモンドの剣",
                    description = listOf("§7強力なダイヤモンドの剣", "§7攻撃力: 7"),
                    price = 2000,
                    itemStack = ItemStack(Material.DIAMOND_SWORD),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "netherite_sword",
                    displayName = "§5ネザライトの剣",
                    description = listOf("§7最強の剣", "§7攻撃力: 8", "§c※最上級"),
                    price = 5000,
                    itemStack = ItemStack(Material.NETHERITE_SWORD),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 武器 - 斧
                ShopItem(
                    id = "iron_axe",
                    displayName = "§f鉄の斧",
                    description = listOf("§7攻撃力の高い斧", "§7攻撃力: 9", "§7高い耐久性"),
                    price = 900,
                    itemStack = ItemStack(Material.IRON_AXE),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "diamond_axe",
                    displayName = "§bダイヤモンドの斧",
                    description = listOf("§7強力な攻撃力", "§7攻撃力: 9"),
                    price = 2200,
                    itemStack = ItemStack(Material.DIAMOND_AXE),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "netherite_axe",
                    displayName = "§5ネザライトの斧",
                    description = listOf("§7最強の斧", "§7攻撃力: 10", "§c※最上級"),
                    price = 5500,
                    itemStack = ItemStack(Material.NETHERITE_AXE),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 武器 - トライデント
                ShopItem(
                    id = "trident",
                    displayName = "§3トライデント",
                    description = listOf("§7投擲可能な武器", "§7攻撃力: 9", "§7遠距離攻撃可能"),
                    price = 3000,
                    itemStack = ItemStack(Material.TRIDENT),
                    category = ShopCategory.WEAPONS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 防具 - 鉄装備セット
                ShopItem(
                    id = "iron_helmet",
                    displayName = "§f鉄のヘルメット",
                    description = listOf("§7実用的な頭防具", "§7防御力: +2", "§7高い耐久性"),
                    price = 600,
                    itemStack = ItemStack(Material.IRON_HELMET),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "iron_chestplate",
                    displayName = "§f鉄のチェストプレート",
                    description = listOf("§7実用的な胸部防具", "§7防御力: +6", "§7高い耐久性"),
                    price = 1200,
                    itemStack = ItemStack(Material.IRON_CHESTPLATE),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "iron_leggings",
                    displayName = "§f鉄のレギンス",
                    description = listOf("§7実用的な脚部防具", "§7防御力: +5", "§7高い耐久性"),
                    price = 1000,
                    itemStack = ItemStack(Material.IRON_LEGGINGS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "iron_boots",
                    displayName = "§f鉄のブーツ",
                    description = listOf("§7実用的な足防具", "§7防御力: +2", "§7高い耐久性"),
                    price = 500,
                    itemStack = ItemStack(Material.IRON_BOOTS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "iron_armor_set",
                    displayName = "§f鉄装備セット",
                    description = listOf("§7鉄の装備一式", "§7ヘルメット+胸当て+レギンス+ブーツ", "§6セット価格でお得！"),
                    price = 2800, // 個別購入より500g安い
                    itemStack = ItemStack(Material.IRON_CHESTPLATE).apply {
                        val meta = itemMeta!!
                        meta.setDisplayName("§f鉄装備セット")
                        itemMeta = meta
                    },
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 防具 - 金装備セット
                ShopItem(
                    id = "golden_helmet",
                    displayName = "§6金のヘルメット",
                    description = listOf("§7金の頭防具", "§7防御力: +2", "§c耐久度が低い"),
                    price = 200,
                    itemStack = ItemStack(Material.GOLDEN_HELMET),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "golden_chestplate",
                    displayName = "§6金のチェストプレート",
                    description = listOf("§7金の胸部防具", "§7防御力: +5", "§c耐久度が低い"),
                    price = 400,
                    itemStack = ItemStack(Material.GOLDEN_CHESTPLATE),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "golden_leggings",
                    displayName = "§6金のレギンス",
                    description = listOf("§7金の脚部防具", "§7防御力: +3", "§c耐久度が低い"),
                    price = 350,
                    itemStack = ItemStack(Material.GOLDEN_LEGGINGS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "golden_boots",
                    displayName = "§6金のブーツ",
                    description = listOf("§7金の足防具", "§7防御力: +1", "§c耐久度が低い"),
                    price = 150,
                    itemStack = ItemStack(Material.GOLDEN_BOOTS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 防具 - ダイヤ装備セット
                ShopItem(
                    id = "diamond_helmet",
                    displayName = "§bダイヤモンドのヘルメット",
                    description = listOf("§7ダイヤの頭防具", "§7防御力: +3"),
                    price = 1500,
                    itemStack = ItemStack(Material.DIAMOND_HELMET),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "diamond_chestplate",
                    displayName = "§bダイヤモンドのチェストプレート",
                    description = listOf("§7ダイヤの胸部防具", "§7防御力: +8"),
                    price = 3000,
                    itemStack = ItemStack(Material.DIAMOND_CHESTPLATE),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "diamond_leggings",
                    displayName = "§bダイヤモンドのレギンス",
                    description = listOf("§7ダイヤの脚部防具", "§7防御力: +6"),
                    price = 2500,
                    itemStack = ItemStack(Material.DIAMOND_LEGGINGS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "diamond_boots",
                    displayName = "§bダイヤモンドのブーツ",
                    description = listOf("§7ダイヤの足防具", "§7防御力: +3"),
                    price = 1200,
                    itemStack = ItemStack(Material.DIAMOND_BOOTS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "diamond_armor_set",
                    displayName = "§bダイヤモンド装備セット",
                    description = listOf("§7ダイヤの装備一式", "§7ヘルメット+胸当て+レギンス+ブーツ", "§6セット価格でお得！"),
                    price = 7200, // 個別購入より1000g安い
                    itemStack = ItemStack(Material.DIAMOND_CHESTPLATE).apply {
                        val meta = itemMeta!!
                        meta.setDisplayName("§bダイヤモンド装備セット")
                        itemMeta = meta
                    },
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 防具 - ネザライト装備セット
                ShopItem(
                    id = "netherite_helmet",
                    displayName = "§5ネザライトのヘルメット",
                    description = listOf("§7最強の頭防具", "§7防御力: +3", "§7ノックバック耐性"),
                    price = 4000,
                    itemStack = ItemStack(Material.NETHERITE_HELMET),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "netherite_chestplate",
                    displayName = "§5ネザライトのチェストプレート",
                    description = listOf("§7最強の胸部防具", "§7防御力: +8", "§7ノックバック耐性"),
                    price = 8000,
                    itemStack = ItemStack(Material.NETHERITE_CHESTPLATE),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "netherite_leggings",
                    displayName = "§5ネザライトのレギンス",
                    description = listOf("§7最強の脚部防具", "§7防御力: +6", "§7ノックバック耐性"),
                    price = 6500,
                    itemStack = ItemStack(Material.NETHERITE_LEGGINGS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "netherite_boots",
                    displayName = "§5ネザライトのブーツ",
                    description = listOf("§7最強の足防具", "§7防御力: +3", "§7ノックバック耐性"),
                    price = 3000,
                    itemStack = ItemStack(Material.NETHERITE_BOOTS),
                    category = ShopCategory.ARMOR,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 特殊アイテム
                ShopItem(
                    id = "elytra",
                    displayName = "§5エリトラ",
                    description = listOf("§7空を飛べる翼", "§c※貴重品"),
                    price = 10000,
                    itemStack = ItemStack(Material.ELYTRA),
                    category = ShopCategory.SPECIAL,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER),
                    maxPurchases = 1
                ),
                ShopItem(
                    id = "ender_pearl_4",
                    displayName = "§5エンダーパール ×4",
                    description = listOf("§7瞬間移動できる"),
                    price = 1000,
                    itemStack = ItemStack(Material.ENDER_PEARL, 4),
                    category = ShopCategory.SPECIAL,
                    allowedRoles = setOf(PlayerRole.RUNNER)
                ),
                
                // 消耗品
                ShopItem(
                    id = "golden_apple",
                    displayName = "§6金のリンゴ",
                    description = listOf("§7回復と一時的な強化"),
                    price = 1500,
                    itemStack = ItemStack(Material.GOLDEN_APPLE),
                    category = ShopCategory.CONSUMABLES,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "speed_potion",
                    displayName = "§b速度のポーション",
                    description = listOf("§73分間の速度上昇II"),
                    price = 800,
                    itemStack = createSpeedPotion(),
                    category = ShopCategory.CONSUMABLES,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "strength_potion",
                    displayName = "§c力のポーション",
                    description = listOf("§73分間の攻撃力上昇", "§cハンター専用"),
                    price = 1200,
                    itemStack = createStrengthPotion(),
                    category = ShopCategory.CONSUMABLES,
                    allowedRoles = setOf(PlayerRole.HUNTER)
                ),
                ShopItem(
                    id = "invisibility_potion",
                    displayName = "§7透明化のポーション",
                    description = listOf("§71分間の透明化", "§aランナー専用"),
                    price = 2000,
                    itemStack = createInvisibilityPotion(),
                    category = ShopCategory.CONSUMABLES,
                    allowedRoles = setOf(PlayerRole.RUNNER),
                    cooldown = 300  // 5分のクールダウン
                ),
                
                // ツール - つるはし
                ShopItem(
                    id = "iron_pickaxe",
                    displayName = "§f鉄のつるはし",
                    description = listOf("§7実用的な採掘ツール", "§7高い耐久性"),
                    price = 600,
                    itemStack = ItemStack(Material.IRON_PICKAXE),
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "diamond_pickaxe",
                    displayName = "§bダイヤモンドのつるはし",
                    description = listOf("§7効率的な採掘", "§7効率強化II付き"),
                    price = 1500,
                    itemStack = ItemStack(Material.DIAMOND_PICKAXE).apply {
                        addEnchantment(Enchantment.EFFICIENCY, 2)
                    },
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "netherite_pickaxe",
                    displayName = "§5ネザライトのつるはし",
                    description = listOf("§7最高速度の採掘", "§7効率強化III付き"),
                    price = 4000,
                    itemStack = ItemStack(Material.NETHERITE_PICKAXE).apply {
                        addEnchantment(Enchantment.EFFICIENCY, 3)
                    },
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.RUNNER)
                ),
                
                // ツール - シャベル
                ShopItem(
                    id = "diamond_shovel",
                    displayName = "§bダイヤモンドのシャベル",
                    description = listOf("§7効率的な掘削", "§7効率強化II付き"),
                    price = 800,
                    itemStack = ItemStack(Material.DIAMOND_SHOVEL).apply {
                        addEnchantment(Enchantment.EFFICIENCY, 2)
                    },
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.RUNNER)
                ),
                
                // ツール - その他
                ShopItem(
                    id = "water_bucket",
                    displayName = "§9水バケツ",
                    description = listOf("§7水の入ったバケツ", "§7落下ダメージ軽減・消火", "§6ネザー進出に必須！"),
                    price = 500,
                    itemStack = ItemStack(Material.WATER_BUCKET),
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "lava_bucket",
                    displayName = "§cマグマバケツ",
                    description = listOf("§7マグマの入ったバケツ", "§7トラップや攻撃に使用", "§c危険・取扱注意"),
                    price = 1200,
                    itemStack = ItemStack(Material.LAVA_BUCKET),
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "flint_and_steel",
                    displayName = "§6火打石と打ち金",
                    description = listOf("§7火をつける道具", "§7攻撃やトラップに"),
                    price = 300,
                    itemStack = ItemStack(Material.FLINT_AND_STEEL),
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "bow",
                    displayName = "§6弓",
                    description = listOf("§7遠距離攻撃武器", "§7矢は別売り"),
                    price = 600,
                    itemStack = ItemStack(Material.BOW),
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                ShopItem(
                    id = "arrow_32",
                    displayName = "§f矢 ×32",
                    description = listOf("§7弓用の矢"),
                    price = 200,
                    itemStack = ItemStack(Material.ARROW, 32),
                    category = ShopCategory.TOOLS,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // 食料
                ShopItem(
                    id = "cooked_beef_16",
                    displayName = "§6ステーキ ×16",
                    description = listOf("§7満腹度を大幅回復"),
                    price = 200,
                    itemStack = ItemStack(Material.COOKED_BEEF, 16),
                    category = ShopCategory.FOOD,
                    allowedRoles = setOf(PlayerRole.HUNTER, PlayerRole.RUNNER)
                ),
                
                // ハンター専用
                ShopItem(
                    id = "compass_upgrade",
                    displayName = "§e追跡コンパス強化",
                    description = listOf("§7より正確な追跡", "§7距離表示の精度向上", "§cハンター専用"),
                    price = 3000,
                    itemStack = ItemStack(Material.COMPASS).apply {
                        val meta = itemMeta!!
                        meta.setDisplayName("§e§l強化型追跡コンパス")
                        itemMeta = meta
                    },
                    category = ShopCategory.SPECIAL,
                    allowedRoles = setOf(PlayerRole.HUNTER),
                    maxPurchases = 1
                )
            )
        }
        
        private fun createSpeedPotion(): ItemStack {
            val potion = ItemStack(Material.POTION)
            val meta = potion.itemMeta as PotionMeta
            meta.addCustomEffect(PotionEffect(PotionEffectType.SPEED, 3600, 1), true) // 3分、速度II
            potion.itemMeta = meta
            return potion
        }
        
        private fun createStrengthPotion(): ItemStack {
            val potion = ItemStack(Material.POTION)
            val meta = potion.itemMeta as PotionMeta
            meta.addCustomEffect(PotionEffect(PotionEffectType.STRENGTH, 3600, 0), true) // 3分、攻撃力上昇I
            potion.itemMeta = meta
            return potion
        }
        
        private fun createInvisibilityPotion(): ItemStack {
            val potion = ItemStack(Material.POTION)
            val meta = potion.itemMeta as PotionMeta
            meta.addCustomEffect(PotionEffect(PotionEffectType.INVISIBILITY, 1200, 0), true) // 1分、透明化
            potion.itemMeta = meta
            return potion
        }
    }
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