package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.entity.Projectile
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.inventory.CraftItemEvent

class EventListener(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val uiManager: UIManager,
    private val messageManager: MessageManager,
    private val roleSelectorMenu: RoleSelectorMenu
) : Listener {
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        // UIManagerにプレイヤー参加を通知
        uiManager.onPlayerJoin(player)
        
        // ネットワークエラーで退出したプレイヤーの再参加処理
        if (gameManager.handleRejoin(player)) {
            return // 再参加処理が成功した場合は終了
        }
        
        // ゲーム専用サーバーのため、ログイン時に自動的にゲームに参加
        when (gameManager.getGameState()) {
            GameState.RUNNING -> {
                // ゲーム進行中は観戦者として参加
                gameManager.addPlayer(player, PlayerRole.SPECTATOR)
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage(messageManager.getMessage(player, "join.game-running"))
                player.sendMessage(messageManager.getMessage(player, "join.next-game"))
                
                // ゲーム状況をタイトルで表示
                uiManager.showTitle(player, messageManager.getMessage(player, "ui.title.manhunt"), messageManager.getMessage(player, "ui.title.game-running-spectator"))
            }
            GameState.WAITING -> {
                // 待機中は観戦者として参加（後で役割変更可能）
                gameManager.addPlayer(player, PlayerRole.SPECTATOR)
                player.gameMode = GameMode.ADVENTURE
                player.sendMessage(messageManager.getMessage(player, "join.welcome"))
                
                // インベントリを初期化
                player.inventory.clear()
                
                // ロール変更アイテムを付与
                giveRoleChangeItem(player)
                
                // 参加案内をタイトルで表示
                uiManager.showTitle(player, messageManager.getMessage(player, "ui.title.manhunt-welcome"), messageManager.getMessage(player, "ui.title.role-selection"))
            }
            else -> {
                // その他の状態では観戦者として参加
                gameManager.addPlayer(player, PlayerRole.SPECTATOR)
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage(messageManager.getMessage(player, "join.spectator"))
            }
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val gameState = gameManager.getGameState()
        val playerRole = gameManager.getPlayerRole(player)
        
        // UIManagerにプレイヤー退出を通知
        uiManager.onPlayerQuit(player)
        
        // バディーシステムにプレイヤー退出を通知
        try {
            plugin.getBuddySystem().onPlayerLeave(player)
        } catch (e: Exception) {
            plugin.logger.warning("バディーシステムのプレイヤー退出処理でエラー: ${e.message}")
        }
        
        
        if (gameState == GameState.RUNNING && playerRole != null) {
            // ゲーム進行中にプレイヤーがサーバーから退出した場合（切断扱い）
            gameManager.removePlayer(player, false)
        } else {
            // ゲーム待機中またはゲームに参加していない場合
            gameManager.removePlayer(player, true)
        }
        
        // WarpCommandのクリーンアップ
        (player.server.getPluginCommand("warp")?.getExecutor() as? WarpCommand)?.onPlayerQuit(player)
    }
    
    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        // ゲーム中でない場合は処理しない
        if (gameManager.getGameState() != GameState.RUNNING) return
        
        // 味方同士のPVPが無効化されているかチェック
        if (!plugin.getConfigManager().isFriendlyFireDisabled()) return
        
        // 攻撃者を取得（投射物の場合は発射者を取得）
        val attacker = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        } ?: return
        
        // 被害者がプレイヤーでない場合は処理しない
        val victim = event.entity as? Player ?: return
        
        // 両者の役割を取得
        val attackerRole = gameManager.getPlayerRole(attacker) ?: return
        val victimRole = gameManager.getPlayerRole(victim) ?: return
        
        // 観戦者は関係ない
        if (attackerRole == PlayerRole.SPECTATOR || victimRole == PlayerRole.SPECTATOR) return
        
        // 同じチーム（役割）の場合はダメージをキャンセル
        if (attackerRole == victimRole) {
            event.isCancelled = true
            
            // 攻撃者に通知（頻度制限付き）
            val lastNotify = friendlyFireNotifications[attacker.uniqueId] ?: 0L
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNotify > 3000) { // 3秒に1回まで
                attacker.sendMessage(messageManager.getMessage(attacker, "pvp.friendly-fire-disabled"))
                friendlyFireNotifications[attacker.uniqueId] = currentTime
            }
        }
    }
    
    // 味方攻撃通知の頻度制限用
    private val friendlyFireNotifications = mutableMapOf<java.util.UUID, Long>()
    
    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity is EnderDragon) {
            val killer = event.entity.killer
            if (killer is Player) {
                gameManager.onEnderDragonDeath(killer)
            }
        } else if (event.entity is Player) {
            val player = event.entity as Player
            // プレイヤー死亡処理をGameManagerに委託
            gameManager.onPlayerDeath(player)
        }
    }


    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        roleSelectorMenu.onInventoryClose(player)
    }
    
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        
        // 右クリックまたは左クリック
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK ||
            event.action == Action.LEFT_CLICK_AIR || event.action == Action.LEFT_CLICK_BLOCK) {
            
            // ロール変更アイテムかチェック
            if (isRoleChangeItem(item) && 
                (gameManager.getGameState() == GameState.WAITING || gameManager.getGameState() == GameState.ENDED)) {
                event.isCancelled = true
                plugin.logger.info("${player.name} clicked role change item, opening menu")
                roleSelectorMenu.openMenu(player)
            }
            
            // ショップアイテムかチェック
            if (isShopItem(item) && gameManager.getGameState() == GameState.RUNNING) {
                event.isCancelled = true
                val shopCommand = gameManager.getPlugin().getCommand("shop")?.getExecutor() as? com.hacklab.manhunt.shop.ShopCommand
                shopCommand?.onCommand(player, gameManager.getPlugin().getCommand("shop")!!, "shop", arrayOf())
            }
        }
    }
    
    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val item = event.itemDrop.itemStack
        
        // ロール変更アイテムのドロップを防止
        if (isRoleChangeItem(item)) {
            event.isCancelled = true
            event.player.sendMessage(messageManager.getMessage(event.player, "item.cannot-drop-role-change"))
        }
        
        // ショップアイテムのドロップを防止
        if (isShopItem(item)) {
            event.isCancelled = true
            event.player.sendMessage(messageManager.getMessage(event.player, "item.cannot-drop-shop"))
        }
    }
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        
        // ロール変更アイテムを死亡時のドロップから除外
        event.drops.removeIf { isRoleChangeItem(it) }
        // ショップアイテムを死亡時のドロップから除外
        event.drops.removeIf { isShopItem(it) }
    }
    
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        
        // ゲーム待機中ならロール変更アイテムを再付与
        if (gameManager.getGameState() == GameState.WAITING) {
            Bukkit.getScheduler().runTaskLater(gameManager.getPlugin(), Runnable {
                player.gameMode = GameMode.ADVENTURE
                giveRoleChangeItem(player)
            }, 1L)
        }
        
        // ゲーム中ならショップアイテムを再付与とゲームモード設定
        if (gameManager.getGameState() == GameState.RUNNING) {
            val role = gameManager.getPlayerRole(player)
            when (role) {
                PlayerRole.HUNTER, PlayerRole.RUNNER -> {
                    Bukkit.getScheduler().runTaskLater(gameManager.getPlugin(), Runnable {
                        player.gameMode = GameMode.SURVIVAL
                        giveShopItem(player)
                    }, 1L)
                }
                PlayerRole.SPECTATOR -> {
                    Bukkit.getScheduler().runTaskLater(gameManager.getPlugin(), Runnable {
                        player.gameMode = GameMode.SPECTATOR
                    }, 1L)
                }
                null -> {
                    // プレイヤーが役割を持っていない場合は何もしない
                }
            }
        }
    }
    
    fun giveRoleChangeItem(player: Player) {
        // 既に持っているかチェック
        if (player.inventory.contents.any { it != null && isRoleChangeItem(it) }) {
            return
        }
        
        val item = ItemStack(Material.WRITABLE_BOOK)
        val meta = item.itemMeta!!
        meta.setDisplayName(messageManager.getMessage(player, "item.role-change.name"))
        meta.lore = listOf(
            messageManager.getMessage(player, "item.role-change.lore1"),
            messageManager.getMessage(player, "item.role-change.lore2")
        )
        // アイテムを光らせる
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        
        // スロット0に配置
        player.inventory.setItem(0, item)
    }
    
    private fun isRoleChangeItem(item: ItemStack): Boolean {
        if (item.type != Material.WRITABLE_BOOK) return false
        val meta = item.itemMeta ?: return false
        val displayName = meta.displayName ?: return false
        
        // デバッグログ
        plugin.logger.info("Checking item: type=${item.type}, name='$displayName'")
        
        // より確実な判定方法
        // カスタムデータ用のLoreをチェック（より確実）
        val lore = meta.lore
        if (lore != null && lore.isNotEmpty()) {
            // 最初のLoreがロール変更用のメッセージかチェック
            val firstLore = lore[0]
            plugin.logger.info("First lore: '$firstLore'")
            if (firstLore.contains("役割選択メニュー") || firstLore.contains("role selection menu")) {
                plugin.logger.info("Matched by lore - this is a role change item")
                return true
            }
        }
        
        // 表示名での判定もフォールバック
        val isMatch = displayName.contains("ロール変更") || displayName.contains("Change Role") ||
               displayName.contains("役割") || displayName.contains("Role")
        
        if (isMatch) {
            plugin.logger.info("Matched by display name - this is a role change item")
        }
        
        return isMatch
    }
    
    private fun giveShopItem(player: Player) {
        // 設定で無効化されている場合は配布しない
        if (!gameManager.getPlugin().getConfigManager().isShopItemEnabled()) {
            return
        }
        
        // プレイヤーの個人設定を確認
        if (!gameManager.getPlugin().getShopManager().getShowShopItemPreference(player)) {
            return
        }
        
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
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        
        // スロット7に配置（コンパスはスロット8）
        player.inventory.setItem(7, item)
    }
    
    private fun isShopItem(item: ItemStack): Boolean {
        if (item.type != Material.EMERALD) return false
        val meta = item.itemMeta ?: return false
        // 名前にショップのキーワードが含まれているかチェック
        val displayName = meta.displayName ?: return false
        return displayName.contains("ショップ") || displayName.contains("Shop") || displayName.contains("商店") || displayName.contains("Store")
    }
    
    @EventHandler
    fun onPlayerToggleSprint(event: PlayerToggleSprintEvent) {
        val player = event.player
        val role = gameManager.getPlayerRole(player)
        
        // ゲーム中のランナーのみ処理
        if (gameManager.getGameState() != GameState.RUNNING || role != PlayerRole.RUNNER) {
            return
        }
        
        // 死亡中のランナーは対象外
        if (player.isDead) {
            return
        }
        
        // スプリント状態に応じて名前タグの可視性を変更
        if (event.isSprinting) {
            // スプリント開始：全員に名前を表示
            gameManager.setRunnerNameTagVisibility(player, true)
        } else {
            // スプリント終了：名前を非表示
            gameManager.setRunnerNameTagVisibility(player, false)
        }
    }
    
    // 最後の位置を記録するためのマップ
    private val lastPositions = mutableMapOf<String, org.bukkit.Location>()
    
    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from
        val to = event.to ?: return
        
        // ゲーム中でなければ処理しない
        if (gameManager.getGameState() != GameState.RUNNING) {
            return
        }
        
        // 死亡中のプレイヤーは対象外
        if (player.isDead) {
            return
        }
        
        // プレイヤーの役割をチェック
        val role = gameManager.getPlayerRole(player)
        if (role == null || role == PlayerRole.SPECTATOR) {
            return
        }
        
        // スプリント中でなければ処理しない
        if (!player.isSprinting) {
            return
        }
        
        // 水平移動距離を計算（Y軸は含めない）
        val distance = Math.sqrt(
            Math.pow(to.x - from.x, 2.0) + Math.pow(to.z - from.z, 2.0)
        )
        
        // 移動距離が十分でなければ処理しない（0.1ブロック未満は無視）
        if (distance < 0.1) {
            return
        }
        
        // 通貨追跡システムに移動を通知
        try {
            gameManager.getPlugin().getCurrencyTracker().onSprintMovement(player, distance)
        } catch (e: Exception) {
            plugin.logger.warning("Error tracking sprint movement for ${player.name}: ${e.message}")
        }
    }

    @EventHandler
    fun onAdvancementDone(event: PlayerAdvancementDoneEvent) {
        val player = event.player
        val advancement = event.advancement

        // ゲーム中でなければ処理しない
        if (gameManager.getGameState() != GameState.RUNNING) {
            return
        }

        // プレイヤーの役割をチェック
        val role = gameManager.getPlayerRole(player)
        if (role == null || role == PlayerRole.SPECTATOR) {
            return
        }

        // レシピのアンロックは除外
        if (advancement.key.key.startsWith("recipes/")) {
            return
        }

        // 通貨を付与
        val economyManager = plugin.getEconomyManager()
        val advancementReward = plugin.getConfigManager().getCurrencyConfig().advancementReward
        val advancementName = advancement.key.key
        economyManager.addMoney(player, advancementReward, com.hacklab.manhunt.economy.EarnReason.Advancement(advancementName))
    }
    
    @EventHandler
    fun onCraftItem(event: CraftItemEvent) {
        // ゲーム中のコンパスクラフトを防止
        if (gameManager.getGameState() != GameState.RUNNING) {
            return
        }
        
        val result = event.recipe.result
        if (result.type == Material.COMPASS) {
            event.isCancelled = true
            val player = event.whoClicked as? Player
            player?.sendMessage("§cゲーム中はコンパスをクラフトできません。")
            plugin.logger.info("Prevented compass crafting by ${player?.name}")
        }
    }
}