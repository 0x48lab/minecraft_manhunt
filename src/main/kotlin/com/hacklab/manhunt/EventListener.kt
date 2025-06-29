package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.EnderDragon
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class EventListener(
    private val gameManager: GameManager,
    private val uiManager: UIManager,
    private val partyManager: PartyManager
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
                player.sendMessage("§7ゲーム進行中のため、観戦者として参加しました。")
                player.sendMessage("§e次回のゲームから役割を選択できます。")
                
                // ゲーム状況をタイトルで表示
                uiManager.showTitle(player, "§6🏃 MANHUNT", "§7ゲーム進行中 - 観戦モード")
            }
            GameState.WAITING -> {
                // 待機中は観戦者として参加（後で役割変更可能）
                gameManager.addPlayer(player, PlayerRole.SPECTATOR)
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("§6[Manhunt] ゲームに参加しました！")
                player.sendMessage("§e/manhunt role <runner|hunter> で役割を選択してください。")
                
                // 参加案内をタイトルで表示
                uiManager.showTitle(player, "§e🎮 MANHUNT", "§f/manhunt role で役割を選択しよう！")
            }
            else -> {
                // その他の状態では観戦者として参加
                gameManager.addPlayer(player, PlayerRole.SPECTATOR)
                player.gameMode = GameMode.SPECTATOR
                player.sendMessage("§7観戦者として参加しました。")
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
        
        // パーティーシステムにプレイヤー退出を通知
        partyManager.handlePlayerLogout(player.name)
        
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
}