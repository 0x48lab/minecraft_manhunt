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
    private val compassTracker: CompassTracker
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
            "ui" -> handleUI(sender, args)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage("§c不明なコマンドです。/manhunt help で使用法を確認してください。")
            }
        }
        return true
    }
    
    
    private fun handleRole(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cプレイヤーのみが実行できるコマンドです。")
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage("§cゲーム開始後は役割を変更できません。")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /manhunt role <runner|hunter|spectator>")
            return
        }
        
        val role = when (args[1].lowercase()) {
            "runner", "逃げる" -> PlayerRole.RUNNER
            "hunter", "追う" -> PlayerRole.HUNTER
            "spectator", "観戦" -> PlayerRole.SPECTATOR
            else -> {
                sender.sendMessage("§c無効な役割です。runner, hunter, spectator のいずれかを指定してください。")
                return
            }
        }
        
        gameManager.setPlayerRole(sender, role)
        val roleText = when (role) {
            PlayerRole.RUNNER -> "逃げる人"
            PlayerRole.HUNTER -> "追う人"
            PlayerRole.SPECTATOR -> "観戦者"
        }
        sender.sendMessage("§a役割を${roleText}に変更しました！")
    }
    
    private fun handleStart(sender: CommandSender) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage("§cゲームはすでに開始されているか、進行中です。")
            return
        }
        
        gameManager.forceStartGame()
        sender.sendMessage("§aゲームを強制開始しました！")
    }
    
    private fun handleCompass(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§cプレイヤーのみが実行できるコマンドです。")
            return
        }
        
        if (gameManager.getGameState() != GameState.RUNNING) {
            sender.sendMessage("§cゲーム進行中のみコンパスを取得できます。")
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
    
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }
        
        try {
            sender.sendMessage("§7設定ファイルをリロードしています...")
            sender.sendMessage("§a設定ファイルのリロードが完了しました。")
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
    
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== Manhunt コマンド ===")
        sender.sendMessage("§e/manhunt role <runner|hunter|spectator> - 役割変更")
        sender.sendMessage("§e/manhunt compass - 追跡コンパスを取得")
        sender.sendMessage("§e/manhunt status - ゲーム状況確認")
        sender.sendMessage("§7※ サーバー参加時に自動的にゲームに参加します")
        
        if (sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("§c=== 管理者コマンド ===")
            sender.sendMessage("§c/manhunt start - ゲーム強制開始")
            sender.sendMessage("§c/manhunt sethunter <プレイヤー> - 追う人に指定")
            sender.sendMessage("§c/manhunt minplayers [数値] - 最小プレイヤー数設定")
            sender.sendMessage("§c/manhunt reload - 設定ファイルをリロード")
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
                    val subcommands = mutableListOf("role", "compass", "status", "help")
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