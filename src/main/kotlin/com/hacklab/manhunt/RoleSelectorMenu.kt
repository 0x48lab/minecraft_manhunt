package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class RoleSelectorMenu(
    private val gameManager: GameManager,
    private val messageManager: MessageManager
) : Listener {

    companion object {
        private const val MENU_TITLE_KEY = "role-selector.menu-title"
        private const val INVENTORY_SIZE = 27 // 3行
    }

    private val openMenus = mutableSetOf<Player>()

    fun openMenu(player: Player) {
        // ゲーム開始前またはゲーム終了後のみ開ける
        if (gameManager.getGameState() != GameState.WAITING && gameManager.getGameState() != GameState.ENDED) {
            player.sendMessage(messageManager.getMessage(player, "role-selector.game-running"))
            return
        }

        val currentRole = gameManager.getPlayerRole(player)
        if (currentRole == null) {
            player.sendMessage(messageManager.getMessage(player, "role-selector.not-in-game"))
            return
        }

        val inventory = createRoleSelectionInventory(player, currentRole)
        openMenus.add(player)
        player.openInventory(inventory)
        
        // デバッグログ
        val plugin = gameManager.getPlugin()
        val title = messageManager.getMessage(player, MENU_TITLE_KEY)
        plugin.logger.info("Opened role selector menu for ${player.name} with title: '$title'")
    }

    private fun createRoleSelectionInventory(player: Player, currentRole: PlayerRole): Inventory {
        val title = messageManager.getMessage(player, MENU_TITLE_KEY)
        val inventory = Bukkit.createInventory(null, INVENTORY_SIZE, title)

        // 背景をグレーのガラスで埋める
        for (i in 0 until INVENTORY_SIZE) {
            inventory.setItem(i, createFillerItem())
        }

        // 役割アイテムを配置（中央に3つ横並び）
        inventory.setItem(11, createRoleItem(player, PlayerRole.RUNNER, currentRole))
        inventory.setItem(13, createRoleItem(player, PlayerRole.HUNTER, currentRole))
        inventory.setItem(15, createRoleItem(player, PlayerRole.SPECTATOR, currentRole))

        // 情報アイテム
        inventory.setItem(4, createInfoItem(player))

        // 閉じるボタン
        inventory.setItem(22, createCloseButton(player))

        return inventory
    }

    private fun createRoleItem(player: Player, role: PlayerRole, currentRole: PlayerRole): ItemStack {
        val (material, nameKey, descKey) = when (role) {
            PlayerRole.RUNNER -> Triple(Material.DIAMOND_SWORD, "role-selector.runner.name", "role-selector.runner.description")
            PlayerRole.HUNTER -> Triple(Material.BOW, "role-selector.hunter.name", "role-selector.hunter.description")
            PlayerRole.SPECTATOR -> Triple(Material.ENDER_EYE, "role-selector.spectator.name", "role-selector.spectator.description")
        }

        val item = ItemStack(material)
        val meta = item.itemMeta!!

        val isSelected = (role == currentRole)
        val prefix = if (isSelected) "§a§l✓ " else "§7"
        
        meta.setDisplayName(prefix + messageManager.getMessage(player, nameKey))
        
        val lore = mutableListOf<String>()
        lore.addAll(messageManager.getMessage(player, descKey).split("\n"))
        lore.add("")
        
        if (isSelected) {
            lore.add(messageManager.getMessage(player, "role-selector.currently-selected"))
        } else {
            lore.add(messageManager.getMessage(player, "role-selector.click-to-select"))
        }

        // 参加人数情報を追加
        val count = when (role) {
            PlayerRole.RUNNER -> gameManager.getAllRunners().size
            PlayerRole.HUNTER -> gameManager.getAllHunters().size
            PlayerRole.SPECTATOR -> gameManager.getAllSpectators().size
        }
        lore.add(messageManager.getMessage(player, "role-selector.current-count", "count" to count))

        meta.lore = lore
        item.itemMeta = meta

        // 選択中の役割にはエンチャント効果を追加
        if (isSelected) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1)
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            item.itemMeta = meta
        }

        return item
    }

    private fun createInfoItem(player: Player): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta!!

        meta.setDisplayName(messageManager.getMessage(player, "role-selector.info.title"))
        meta.lore = listOf(
            messageManager.getMessage(player, "role-selector.info.description-1"),
            messageManager.getMessage(player, "role-selector.info.description-2"),
            "",
            messageManager.getMessage(player, "role-selector.info.waiting-state"),
            messageManager.getMessage(player, "role-selector.info.min-players", 
                "count" to gameManager.getMinPlayers())
        )

        item.itemMeta = meta
        return item
    }

    private fun createCloseButton(player: Player): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta!!

        meta.setDisplayName(messageManager.getMessage(player, "role-selector.button.close"))
        item.itemMeta = meta
        return item
    }

    private fun createFillerItem(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta!!
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory

        // メニュータイトルをチェック
        val view = event.view
        val expectedTitle = messageManager.getMessage(player, MENU_TITLE_KEY)
        
        // デバッグログ
        val plugin = gameManager.getPlugin()
        plugin.logger.info("InventoryClick: viewed title='${view.title}', expected='$expectedTitle'")
        
        if (view.title != expectedTitle) return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val slot = event.slot

        when (slot) {
            11 -> { // ランナー選択
                selectRole(player, PlayerRole.RUNNER)
            }
            13 -> { // ハンター選択
                selectRole(player, PlayerRole.HUNTER)
            }
            15 -> { // 観戦者選択
                selectRole(player, PlayerRole.SPECTATOR)
            }
            22 -> { // 閉じる
                if (clickedItem.type == Material.BARRIER) {
                    player.closeInventory()
                }
            }
        }
    }

    private fun selectRole(player: Player, newRole: PlayerRole) {
        val currentRole = gameManager.getPlayerRole(player)
        
        if (currentRole == newRole) {
            player.sendMessage(messageManager.getMessage(player, "role-selector.already-selected", 
                "role" to messageManager.getMessage(player, "role.${newRole.name.lowercase()}")))
            return
        }

        // 役割変更を実行
        try {
            gameManager.setPlayerRole(player, newRole)
            val roleName = messageManager.getMessage(player, "role.${newRole.name.lowercase()}")
            player.sendMessage(messageManager.getMessage(player, "role.changed", "role" to roleName))
            
            // 音響効果
            player.playSound(player.location, org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
            
            // メニューを閉じる
            player.closeInventory()
        } catch (e: Exception) {
            player.sendMessage(messageManager.getMessage(player, "role-selector.change-failed"))
        }
    }

    fun onInventoryClose(player: Player) {
        openMenus.remove(player)
    }

    fun cleanup() {
        openMenus.clear()
    }
}