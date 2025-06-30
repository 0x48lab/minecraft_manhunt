package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import kotlin.collections.listOf

class ManhuntCommand(
    private val gameManager: GameManager,
    private val compassTracker: CompassTracker,
    private val spectatorMenu: SpectatorMenu,
    private val messageManager: MessageManager
) : CommandExecutor, TabCompleter {
    
    private val configManager: ConfigManager
        get() = gameManager.configManager
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "role" -> handleRole(sender, args)
            "start" -> handleStart(sender)
            "compass" -> handleCompass(sender)
            "status" -> handleStatus(sender)
            "sethunter" -> handleSetHunter(sender, args)
            "minplayers" -> handleMinPlayers(sender, args)
            "reload" -> handleReload(sender, args)
            "ui" -> handleUI(sender, args)
            "spectate" -> handleSpectate(sender)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage(messageManager.getMessage(sender as? Player, "command.unknown"))
            }
        }
        return true
    }
    
    
    private fun handleRole(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "command.player-only"))
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage(messageManager.getMessage(sender, "role.game-running"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage(sender, "command.usage", "/manhunt role <runner|hunter|spectator>"))
            return
        }
        
        val role = when (args[1].lowercase()) {
            "runner", "逃げる" -> PlayerRole.RUNNER
            "hunter", "追う" -> PlayerRole.HUNTER
            "spectator", "観戦" -> PlayerRole.SPECTATOR
            else -> {
                sender.sendMessage(messageManager.getMessage(sender, "role.invalid"))
                return
            }
        }
        
        gameManager.setPlayerRole(sender, role)
        val roleText = messageManager.getMessage(sender, "role.${role.name.lowercase()}")
        sender.sendMessage(messageManager.getMessage(sender, "role.changed", roleText))
    }
    
    private fun handleStart(sender: CommandSender) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "command.no-permission"))
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.game-already-started"))
            return
        }
        
        gameManager.forceStartGame()
        sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.force-start"))
    }
    
    private fun handleCompass(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "command.player-only"))
            return
        }
        
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage(messageManager.getMessage(sender, "compass.game-only"))
            return
        }
        
        compassTracker.giveCompass(sender)
    }
    
    private fun handleStatus(sender: CommandSender) {
        val state = when (gameManager.getGameState()) {
            GameState.WAITING -> "待機中"
            GameState.STARTING -> "開始中"
            GameState.RUNNING -> "進行中"
            GameState.ENDED -> "終了"
        }
        
        val hunters = gameManager.getAllHunters()
        val runners = gameManager.getAllRunners()
        val spectators = gameManager.getAllSpectators()
        
        sender.sendMessage("§6=== Manhunt ゲーム状況 ===")
        sender.sendMessage("§eゲーム状態: $state")
        sender.sendMessage("§e最小プレイヤー数: ${gameManager.getMinPlayers()}")
        sender.sendMessage("§a逃げる人: ${runners.size}人 ${if (runners.isNotEmpty()) runners.map { it.name } else ""}")
        sender.sendMessage("§c追う人: ${hunters.size}人 ${if (hunters.isNotEmpty()) hunters.map { it.name } else ""}")
        sender.sendMessage("§7観戦者: ${spectators.size}人 ${if (spectators.isNotEmpty()) spectators.map { it.name } else ""}")
        
        // 開始条件のチェック状況
        val totalPlayers = hunters.size + runners.size + spectators.size
        sender.sendMessage("§e総プレイヤー数: $totalPlayers")
        
        if (gameManager.getGameState() == GameState.WAITING) {
            val canStart = totalPlayers >= gameManager.getMinPlayers() && hunters.isNotEmpty() && runners.isNotEmpty()
            sender.sendMessage("§e自動開始可能: ${if (canStart) "§a✓" else "§c✗"}")
            
            if (!canStart) {
                if (totalPlayers < gameManager.getMinPlayers()) {
                    sender.sendMessage("§c  - プレイヤー数不足 (${totalPlayers}/${gameManager.getMinPlayers()})")
                }
                if (hunters.isEmpty()) {
                    sender.sendMessage("§c  - ハンターが不足")
                }
                if (runners.isEmpty()) {
                    sender.sendMessage("§c  - ランナーが不足")
                }
            }
        }
    }
    
    private fun handleSetHunter(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /manhunt sethunter <プレイヤー名>")
            return
        }
        
        val playerName = args[1]
        if (playerName.isBlank()) {
            sender.sendMessage("§cプレイヤー名を指定してください。")
            return
        }
        
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null || !targetPlayer.isOnline) {
            sender.sendMessage("§cプレイヤー '$playerName' がオンラインではありません。")
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage("§cゲーム開始後は役割を変更できません。")
            return
        }
        
        gameManager.setPlayerRole(targetPlayer, PlayerRole.HUNTER)
        sender.sendMessage("§a${targetPlayer.name}を追う人に設定しました。")
        targetPlayer.sendMessage("§cあなたは追う人に設定されました！")
    }
    
    private fun handleMinPlayers(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§c現在の最小プレイヤー数: ${gameManager.getMinPlayers()}")
            sender.sendMessage("§c変更するには: /manhunt minplayers <数値>")
            return
        }
        
        val countStr = args[1]
        if (countStr.isBlank()) {
            sender.sendMessage("§c数値を指定してください。")
            return
        }
        
        val count = countStr.toIntOrNull()
        if (count == null) {
            sender.sendMessage("§c'$countStr' は有効な数値ではありません。")
            return
        }
        
        if (count < 2) {
            sender.sendMessage("§c最小プレイヤー数は2以上である必要があります。")
            return
        }
        
        if (count > 100) {
            sender.sendMessage("§c最小プレイヤー数は100以下である必要があります。")
            return
        }
        
        gameManager.setMinPlayers(count)
        sender.sendMessage("§a最小プレイヤー数を${count}に設定しました。")
    }
    
    private fun handleReload(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }
        
        val reloadType = if (args.size > 1) args[1].lowercase() else "all"
        
        try {
            when (reloadType) {
                "config" -> {
                    sender.sendMessage("§7config.yml をリロードしています...")
                    gameManager.configManager.reloadConfig()
                    sender.sendMessage("§aconfig.yml のリロードが完了しました。")
                }
                "shop" -> {
                    sender.sendMessage("§7shop.yml をリロードしています...")
                    gameManager.getPlugin().getShopManager().reloadShopConfig()
                    sender.sendMessage("§ashop.yml のリロードが完了しました。")
                }
                "all" -> {
                    sender.sendMessage("§7全設定ファイルをリロードしています...")
                    gameManager.configManager.reloadConfig()
                    gameManager.getPlugin().getShopManager().reloadShopConfig()
                    sender.sendMessage("§a全設定ファイルのリロードが完了しました。")
                }
                else -> {
                    sender.sendMessage("§c使用法: /manhunt reload [config|shop|all]")
                    return
                }
            }
            sender.sendMessage("§e注意: 一部の設定はゲーム再開始後に反映されます。")
        } catch (e: Exception) {
            sender.sendMessage("§c設定ファイルのリロードに失敗しました: ${e.message}")
        }
    }
    
    private fun handleUI(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /manhunt ui <toggle|status>")
            return
        }
        
        when (args[1].lowercase()) {
            "status" -> {
                sender.sendMessage("§6=== UI表示設定 ===")
                sender.sendMessage("§eスコアボード: ${if (configManager.isScoreboardEnabled()) "§a有効" else "§c無効"}")
                sender.sendMessage("§eActionBar: ${if (configManager.isActionBarEnabled()) "§a有効" else "§c無効"}")
                sender.sendMessage("§eBossBar: ${if (configManager.isBossBarEnabled()) "§a有効" else "§c無効"}")
                sender.sendMessage("§eタイトル: ${if (configManager.isTitleEnabled()) "§a有効" else "§c無効"}")
            }
            "toggle" -> {
                sender.sendMessage("§7現在のUI設定の表示・非表示は config.yml で変更できます。")
                sender.sendMessage("§7/manhunt reload でconfig.ymlの変更を反映してください。")
            }
            else -> {
                sender.sendMessage("§c使用法: /manhunt ui <toggle|status>")
            }
        }
    }
    
    private fun handleSpectate(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§cプレイヤーのみが実行できるコマンドです。")
            return
        }
        
        spectatorMenu.openMenu(sender)
    }
    
    
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== Manhunt コマンド ===")
        sender.sendMessage("§e/manhunt role <runner|hunter|spectator> - 役割変更")
        sender.sendMessage("§e/manhunt compass - 仮想追跡コンパスを有効化")
        sender.sendMessage("§e/manhunt status - ゲーム状況確認")
        sender.sendMessage("§e/manhunt spectate - 観戦メニューを開く（観戦者のみ）")
        sender.sendMessage("§7※ サーバー参加時に自動的にゲームに参加します")
        sender.sendMessage("")
        sender.sendMessage("§a=== その他のコマンド ===")
        sender.sendMessage("§a/r <メッセージ> - チームチャット（味方のみ）")
        sender.sendMessage("§a/pos - 座標を味方に共有")
        sender.sendMessage("§a/shop - ショップを開く")
        sender.sendMessage("§a/shop balance - 所持金確認")
        sender.sendMessage("")
        sender.sendMessage("§b=== 仮想コンパスの使い方 ===")
        sender.sendMessage("§7• 空手で右クリック = 最寄りランナーを追跡")
        sender.sendMessage("§7• パーティクルと矢印で方向を表示")
        sender.sendMessage("§7• アイテムドロップや重複の心配なし")
        
        if (sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("")
            sender.sendMessage("§c=== 管理者コマンド ===")
            sender.sendMessage("§c/manhunt start - ゲーム強制開始")
            sender.sendMessage("§c/manhunt sethunter <プレイヤー> - 追う人に指定")
            sender.sendMessage("§c/manhunt minplayers [数値] - 最小プレイヤー数設定")
            sender.sendMessage("§c/manhunt reload [config|shop|all] - 設定ファイルをリロード")
            sender.sendMessage("§c/manhunt ui <toggle|status> - UI表示制御")
        }
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        try {
            return when (args.size) {
                1 -> {
                    val subcommands = mutableListOf("role", "compass", "status", "spectate", "help")
                    if (sender.hasPermission("manhunt.admin")) {
                        subcommands.addAll(listOf("start", "sethunter", "minplayers", "ui", "reload"))
                    }
                    val input = args.getOrNull(0)?.lowercase() ?: ""
                    subcommands.filter { it.startsWith(input) }
                }
                2 -> {
                    val subcommand = args.getOrNull(0)?.lowercase() ?: return emptyList()
                    val input = args.getOrNull(1)?.lowercase() ?: ""
                    when (subcommand) {
                        "role" -> listOf("runner", "hunter", "spectator").filter { it.startsWith(input) }
                        "sethunter" -> {
                            if (sender.hasPermission("manhunt.admin")) {
                                Bukkit.getOnlinePlayers().mapNotNull { it?.name }.filter { it.lowercase().startsWith(input) }
                            } else {
                                emptyList()
                            }
                        }
                        "ui" -> {
                            if (sender.hasPermission("manhunt.admin")) {
                                listOf("toggle", "status").filter { it.startsWith(input) }
                            } else {
                                emptyList()
                            }
                        }
                        "reload" -> {
                            if (sender.hasPermission("manhunt.admin")) {
                                listOf("config", "shop", "all").filter { it.startsWith(input) }
                            } else {
                                emptyList()
                            }
                        }
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }
}