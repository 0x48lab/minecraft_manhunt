package com.hacklab.manhunt.shop

import com.hacklab.manhunt.Main
import com.hacklab.manhunt.PlayerRole
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.io.File
import java.io.InputStreamReader

/**
 * ショップ設定ファイル管理クラス
 */
class ShopConfigManager(private val plugin: Main) {
    private var shopConfig: FileConfiguration? = null
    private var shopConfigFile: File? = null
    
    init {
        loadShopConfig()
    }
    
    /**
     * shop.yml設定ファイルを読み込み
     */
    fun loadShopConfig() {
        shopConfigFile = File(plugin.dataFolder, "shop.yml")
        
        if (!shopConfigFile!!.exists()) {
            plugin.logger.info("shop.yml が見つかりません。デフォルトファイルを作成しています...")
            plugin.saveResource("shop.yml", false)
        }
        
        shopConfig = YamlConfiguration.loadConfiguration(shopConfigFile!!)
        
        // デフォルト設定を読み込み（リソースファイルから）
        val defConfigStream = plugin.getResource("shop.yml")
        if (defConfigStream != null) {
            val defConfig = YamlConfiguration.loadConfiguration(InputStreamReader(defConfigStream))
            shopConfig!!.setDefaults(defConfig)
        }
        
        plugin.logger.info("shop.yml を読み込みました")
    }
    
    /**
     * 設定ファイルをリロード
     */
    fun reloadShopConfig() {
        shopConfig = YamlConfiguration.loadConfiguration(shopConfigFile!!)
        plugin.logger.info("shop.yml をリロードしました")
    }
    
    /**
     * 設定ファイルからショップアイテムを読み込み
     */
    fun loadShopItems(): List<ShopItem> {
        val items = mutableListOf<ShopItem>()
        val itemsSection = shopConfig?.getConfigurationSection("items")
        
        if (itemsSection == null) {
            plugin.logger.warning("shop.yml の items セクションが見つかりません")
            return emptyList()
        }
        
        for (itemId in itemsSection.getKeys(false)) {
            try {
                val itemSection = itemsSection.getConfigurationSection(itemId)
                if (itemSection != null) {
                    val shopItem = createShopItemFromConfig(itemId, itemSection)
                    if (shopItem != null) {
                        items.add(shopItem)
                        plugin.logger.info("ショップアイテムを読み込み: $itemId")
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("ショップアイテム $itemId の読み込みでエラー: ${e.message}")
            }
        }
        
        plugin.logger.info("ショップアイテム ${items.size} 個を読み込みました")
        return items
    }
    
    /**
     * 設定からShopItemを作成
     */
    private fun createShopItemFromConfig(itemId: String, config: org.bukkit.configuration.ConfigurationSection): ShopItem? {
        try {
            // 必須項目の取得
            val materialName = config.getString("material") ?: return null
            val price = config.getInt("price", 0)
            val categoryName = config.getString("category") ?: "SPECIAL"
            
            // display-nameの取得（なければメッセージシステムから）
            val displayName = config.getString("display-name") 
                ?: plugin.getMessageManager().getMessage("shop-extended.items.$itemId.name")
            
            // マテリアルの検証
            val material = try {
                Material.valueOf(materialName.uppercase())
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("無効なマテリアル: $materialName (アイテム: $itemId)")
                return null
            }
            
            // カテゴリの検証
            val category = try {
                ShopCategory.valueOf(categoryName.uppercase())
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("無効なカテゴリ: $categoryName (アイテム: $itemId)")
                ShopCategory.SPECIAL
            }
            
            // アイテムスタックの作成
            val amount = config.getInt("amount", 1)
            val itemStack = ItemStack(material, amount)
            
            // エンチャントの追加
            val enchantments = config.getConfigurationSection("enchantments")
            if (enchantments != null) {
                for (enchantKey in enchantments.getKeys(false)) {
                    val enchantSection = enchantments.getConfigurationSection(enchantKey)
                    if (enchantSection != null) {
                        val enchantType = enchantSection.getString("type")
                        val level = enchantSection.getInt("level", 1)
                        
                        try {
                            val enchantment = Enchantment.getByName(enchantType?.uppercase() ?: "")
                            if (enchantment != null) {
                                itemStack.addEnchantment(enchantment, level)
                            }
                        } catch (e: Exception) {
                            plugin.logger.warning("エンチャント追加エラー: $enchantType (アイテム: $itemId)")
                        }
                    }
                }
            }
            
            // ポーション効果の追加
            if (material == Material.POTION) {
                val potionEffects = config.getConfigurationSection("potion-effects")
                if (potionEffects != null) {
                    val meta = itemStack.itemMeta as? PotionMeta
                    if (meta != null) {
                        for (effectKey in potionEffects.getKeys(false)) {
                            val effectSection = potionEffects.getConfigurationSection(effectKey)
                            if (effectSection != null) {
                                val effectType = effectSection.getString("type")
                                val duration = effectSection.getInt("duration", 600)
                                val amplifier = effectSection.getInt("amplifier", 0)
                                
                                try {
                                    val potionEffectType = PotionEffectType.getByName(effectType?.uppercase() ?: "")
                                    if (potionEffectType != null) {
                                        val effect = PotionEffect(potionEffectType, duration, amplifier)
                                        meta.addCustomEffect(effect, true)
                                    }
                                } catch (e: Exception) {
                                    plugin.logger.warning("ポーション効果追加エラー: $effectType (アイテム: $itemId)")
                                }
                            }
                        }
                        itemStack.itemMeta = meta
                    }
                }
            }
            
            // 説明文の取得（なければメッセージシステムから）
            val description = if (config.contains("description")) {
                config.getStringList("description")
            } else {
                plugin.getMessageManager().getMessageList("shop-extended.items.$itemId.description")
            }
            
            // 許可された役割の取得
            val allowedRoleStrings = config.getStringList("allowed-roles")
            val allowedRoles = mutableSetOf<PlayerRole>()
            for (roleString in allowedRoleStrings) {
                try {
                    val role = PlayerRole.valueOf(roleString.uppercase())
                    allowedRoles.add(role)
                } catch (e: IllegalArgumentException) {
                    plugin.logger.warning("無効な役割: $roleString (アイテム: $itemId)")
                }
            }
            
            // デフォルトで全役割を許可（空の場合）
            if (allowedRoles.isEmpty()) {
                allowedRoles.addAll(setOf(PlayerRole.HUNTER, PlayerRole.RUNNER))
            }
            
            // オプション項目の取得
            val maxPurchases = config.getInt("max-purchases", -1)
            val cooldown = config.getInt("cooldown", 0)
            
            // セットアイテムの処理
            val setItems = config.getStringList("set-items")
            
            return ShopItem(
                id = itemId,
                displayName = displayName,
                description = description,
                price = price,
                itemStack = itemStack,
                category = category,
                allowedRoles = allowedRoles,
                maxPurchases = maxPurchases,
                cooldown = cooldown,
                setItems = if (setItems.isNotEmpty()) setItems else null
            )
            
        } catch (e: Exception) {
            plugin.logger.severe("ショップアイテム $itemId の作成でエラー: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * カテゴリ設定を読み込み
     */
    fun loadCategories(): Map<ShopCategory, CategoryConfig> {
        val categories = mutableMapOf<ShopCategory, CategoryConfig>()
        val categoriesSection = shopConfig?.getConfigurationSection("categories")
        
        if (categoriesSection != null) {
            for (categoryKey in categoriesSection.getKeys(false)) {
                try {
                    val categorySection = categoriesSection.getConfigurationSection(categoryKey)
                    if (categorySection != null) {
                        val category = ShopCategory.valueOf(categoryKey.uppercase())
                        // 設定ファイルでdisplay-nameが指定されていればそれを使用、なければnullにしてShopManagerで言語別に取得
                        val displayName = categorySection.getString("display-name")
                        val iconMaterial = categorySection.getString("icon") ?: category.icon.name
                        
                        val icon = try {
                            Material.valueOf(iconMaterial.uppercase())
                        } catch (e: IllegalArgumentException) {
                            category.icon
                        }
                        
                        categories[category] = CategoryConfig(displayName, icon)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("カテゴリ $categoryKey の読み込みでエラー: ${e.message}")
                }
            }
        }
        
        return categories
    }
    
    /**
     * カテゴリ設定データクラス
     */
    data class CategoryConfig(
        val displayName: String?,
        val icon: Material
    )
    
    /**
     * 設定ファイルの存在確認
     */
    fun configExists(): Boolean {
        return shopConfigFile?.exists() == true
    }
}