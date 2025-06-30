package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventListener(
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
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage(messageManager.getMessage(player, "join.welcome"))
                player.sendMessage(messageManager.getMessage(player, "join.role-select"))
                
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
        
        
        if (gameState == GameState.RUNNING && playerRole != null) {
            // ゲーム進行中にプレイヤーがサーバーから退出した場合（切断扱い）
            gameManager.removePlayer(player, false)
        } else {
            // ゲーム待機中またはゲームに参加していない場合
            gameManager.removePlayer(player, true)
        }
    }
    
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
}