package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import kotlin.math.ceil
import kotlin.math.min

class SpectatorMenu(
    private val gameManager: GameManager
) : Listener {
    
    companion object {
        private const val ITEMS_PER_PAGE = 28 // 7x4 グリッド（ナビゲーション用の行を除く）
        private const val INVENTORY_SIZE = 54 // 6行（9x6）
        private const val MENU_TITLE = "§6観戦メニュー"
    }
    
    private val playerMenus = mutableMapOf<Player, MenuState>()
    
    data class MenuState(
        var currentPage: Int = 0,
        var lastUpdate: Long = 0
    )
    
    fun openMenu(spectator: Player) {
        if (gameManager.getPlayerRole(spectator) != PlayerRole.SPECTATOR) {
            spectator.sendMessage("§c観戦者のみがこのメニューを使用できます。")
            return
        }
        
        val menuState = playerMenus.getOrPut(spectator) { MenuState() }
        val inventory = createInventory(menuState.currentPage)
        spectator.openInventory(inventory)
    }
    
    private fun createInventory(page: Int): Inventory {
        val allPlayers = mutableListOf<PlayerInfo>()
        
        // ランナーを追加
        gameManager.getAllRunners()
            .filter { it.isOnline && !it.isDead }
            .forEach { allPlayers.add(PlayerInfo(it, PlayerRole.RUNNER)) }
        
        // ハンターを追加
        gameManager.getAllHunters()
            .filter { it.isOnline && !it.isDead }
            .forEach { allPlayers.add(PlayerInfo(it, PlayerRole.HUNTER)) }
        
        val totalPages = ceil(allPlayers.size.toDouble() / ITEMS_PER_PAGE).toInt().coerceAtLeast(1)
        val currentPage = page.coerceIn(0, totalPages - 1)
        
        val inventory = Bukkit.createInventory(
            null, 
            INVENTORY_SIZE, 
            "$MENU_TITLE §7- ページ ${currentPage + 1}/$totalPages"
        )
        
        // プレイヤーヘッドを配置
        val startIndex = currentPage * ITEMS_PER_PAGE
        val endIndex = min(startIndex + ITEMS_PER_PAGE, allPlayers.size)
        
        for (i in startIndex until endIndex) {
            val playerInfo = allPlayers[i]
            val slot = i - startIndex
            
            // スロット位置を調整（下2行を空ける）
            val adjustedSlot = if (slot >= 21) slot + 6 else if (slot >= 14) slot + 3 else if (slot >= 7) slot else slot
            
            inventory.setItem(adjustedSlot, createPlayerHead(playerInfo))
        }
        
        // ナビゲーションアイテムを配置
        addNavigationItems(inventory, currentPage, totalPages)
        
        return inventory
    }
    
    private fun createPlayerHead(playerInfo: PlayerInfo): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta
        
        meta.owningPlayer = playerInfo.player
        
        val roleColor = when (playerInfo.role) {
            PlayerRole.RUNNER -> "§a"
            PlayerRole.HUNTER -> "§c"
            else -> "§7"
        }
        
        val roleText = when (playerInfo.role) {
            PlayerRole.RUNNER -> "逃げる人"
            PlayerRole.HUNTER -> "追う人"
            else -> "観戦者"
        }
        
        meta.setDisplayName("$roleColor${playerInfo.player.name}")
        
        val lore = mutableListOf<String>()
        lore.add("§7役割: $roleColor$roleText")
        lore.add("§7体力: §c${String.format("%.1f", playerInfo.player.health)}§7/§c20.0")
        lore.add("§7ワールド: §e${playerInfo.player.world.name}")
        lore.add("")
        lore.add("§eクリックでテレポート！")
        
        meta.lore = lore
        item.itemMeta = meta
        
        return item
    }
    
    private fun addNavigationItems(inventory: Inventory, currentPage: Int, totalPages: Int) {
        // 前のページボタン
        if (currentPage > 0) {
            val previousButton = ItemStack(Material.ARROW)
            val meta = previousButton.itemMeta
            meta?.setDisplayName("§a◀ 前のページ")
            meta?.lore = listOf("§7ページ ${currentPage}へ")
            previousButton.itemMeta = meta
            inventory.setItem(45, previousButton)
        }
        
        // 次のページボタン
        if (currentPage < totalPages - 1) {
            val nextButton = ItemStack(Material.ARROW)
            val meta = nextButton.itemMeta
            meta?.setDisplayName("§a次のページ ▶")
            meta?.lore = listOf("§7ページ ${currentPage + 2}へ")
            nextButton.itemMeta = meta
            inventory.setItem(53, nextButton)
        }
        
        // 情報アイテム
        val infoItem = ItemStack(Material.BOOK)
        val infoMeta = infoItem.itemMeta
        infoMeta?.setDisplayName("§6観戦メニュー")
        infoMeta?.lore = listOf(
            "§7プレイヤーをクリックして",
            "§7テレポートできます。",
            "",
            "§a● ランナー",
            "§c● ハンター"
        )
        infoItem.itemMeta = infoMeta
        inventory.setItem(49, infoItem)
        
        // 閉じるボタン
        val closeButton = ItemStack(Material.BARRIER)
        val closeMeta = closeButton.itemMeta
        closeMeta?.setDisplayName("§c閉じる")
        closeButton.itemMeta = closeMeta
        inventory.setItem(48, closeButton)
        
        // リフレッシュボタン
        val refreshButton = ItemStack(Material.COMPASS)
        val refreshMeta = refreshButton.itemMeta
        refreshMeta?.setDisplayName("§b更新")
        refreshMeta?.lore = listOf("§7プレイヤーリストを更新")
        refreshButton.itemMeta = refreshMeta
        inventory.setItem(50, refreshButton)
    }
    
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = event.inventory
        
        // メニュータイトルをチェック
        val view = event.view
        if (!view.title.startsWith(MENU_TITLE)) return
        
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        val slot = event.slot
        
        when (slot) {
            45 -> { // 前のページ
                if (clickedItem.type == Material.ARROW) {
                    val menuState = playerMenus[player] ?: return
                    menuState.currentPage--
                    player.openInventory(createInventory(menuState.currentPage))
                }
            }
            53 -> { // 次のページ
                if (clickedItem.type == Material.ARROW) {
                    val menuState = playerMenus[player] ?: return
                    menuState.currentPage++
                    player.openInventory(createInventory(menuState.currentPage))
                }
            }
            48 -> { // 閉じる
                if (clickedItem.type == Material.BARRIER) {
                    player.closeInventory()
                }
            }
            50 -> { // 更新
                if (clickedItem.type == Material.COMPASS) {
                    val menuState = playerMenus[player] ?: return
                    player.openInventory(createInventory(menuState.currentPage))
                }
            }
            else -> {
                // プレイヤーヘッドをクリック
                if (clickedItem.type == Material.PLAYER_HEAD) {
                    val meta = clickedItem.itemMeta as? SkullMeta ?: return
                    val targetPlayer = meta.owningPlayer?.player
                    
                    if (targetPlayer != null && targetPlayer.isOnline) {
                        player.teleport(targetPlayer.location)
                        player.closeInventory()
                        player.sendMessage("§a${targetPlayer.name}にテレポートしました！")
                    } else {
                        player.sendMessage("§cそのプレイヤーはオンラインではありません。")
                    }
                }
            }
        }
    }
    
    fun cleanup() {
        playerMenus.clear()
    }
    
    private data class PlayerInfo(
        val player: Player,
        val role: PlayerRole
    )
}