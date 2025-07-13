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
    private val messageManager: MessageManager,
    private val roleSelectorMenu: RoleSelectorMenu
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
            "roles" -> handleRoleMenu(sender)
            "start" -> handleStart(sender)
            "stop", "end" -> handleStop(sender)
            "compass" -> handleCompass(sender)
            "status" -> handleStatus(sender)
            "sethunter" -> handleSetHunter(sender, args)
            "setrunner" -> handleSetRunner(sender, args)
            "setspectator" -> handleSetSpectator(sender, args)
            "minplayers" -> handleMinPlayers(sender, args)
            "reload" -> handleReload(sender, args)
            "ui" -> handleUI(sender, args)
            "spectate" -> handleSpectate(sender)
            "respawntime" -> handleRespawnTime(sender, args)
            "reset" -> handleReset(sender)
            "help" -> showHelp(sender)
            "validate-messages" -> handleValidateMessages(sender)
            "diagnose" -> {
                if (!sender.hasPermission("manhunt.admin")) {
                    sender.sendMessage(messageManager.getMessage(sender, "command.no-permission"))
                    return false
                }
                // メッセージシステムの診断を実行
                messageManager.diagnose()
                sender.sendMessage("§a[Manhunt] Diagnostic information has been output to the server console.")
                true
            }
            "give" -> handleGive(sender, args)
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
            sender.sendMessage(messageManager.getMessage(sender, "command.usage", mapOf("usage" to "/manhunt role <runner|hunter|spectator>")))
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
        sender.sendMessage(messageManager.getMessage(sender, "role.changed", mapOf("role" to roleText)))
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
    
    private fun handleStop(sender: CommandSender) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "command.no-permission"))
            return
        }
        
        val currentState = gameManager.getGameState()
        if (currentState == GameState.WAITING) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.game-not-running"))
            return
        }
        
        // ゲームを強制終了
        gameManager.forceEndGame()
        sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.force-stop"))
        
        // 全プレイヤーに通知
        gameManager.getPlugin().server.onlinePlayers.forEach { player ->
            player.sendMessage(messageManager.getMessage(player, "game.force-stopped-by-admin", mapOf("admin" to sender.name)))
        }
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
            GameState.WAITING -> messageManager.getMessage("command-interface.status-waiting")
            GameState.STARTING -> messageManager.getMessage("command-interface.status-starting")
            GameState.RUNNING -> messageManager.getMessage("command-interface.status-running")
            GameState.ENDED -> messageManager.getMessage("command-interface.status-ended")
        }
        
        val hunters = gameManager.getAllHunters()
        val runners = gameManager.getAllRunners()
        val spectators = gameManager.getAllSpectators()
        
        sender.sendMessage(messageManager.getMessage("command-interface.status-game-header"))
        sender.sendMessage(messageManager.getMessage("command-interface.status-game-state", mapOf("state" to state)))
        sender.sendMessage(messageManager.getMessage("command-interface.status-min-players", mapOf("count" to gameManager.getMinPlayers())))
        sender.sendMessage(messageManager.getMessage("command-interface.status-runners-list", mapOf("count" to runners.size, "players" to if (runners.isNotEmpty()) runners.map { it.name } else "")))
        sender.sendMessage(messageManager.getMessage("command-interface.status-hunters-list", mapOf("count" to hunters.size, "players" to if (hunters.isNotEmpty()) hunters.map { it.name } else "")))
        sender.sendMessage(messageManager.getMessage("command-interface.status-spectators-list", mapOf("count" to spectators.size, "players" to if (spectators.isNotEmpty()) spectators.map { it.name } else "")))
        
        // 開始条件のチェック状況
        val totalPlayers = hunters.size + runners.size + spectators.size
        sender.sendMessage(messageManager.getMessage("command-interface.status-total-players", mapOf("count" to totalPlayers)))
        
        if (gameManager.getGameState() == GameState.WAITING) {
            val canStart = totalPlayers >= gameManager.getMinPlayers() && hunters.isNotEmpty() && runners.isNotEmpty()
            sender.sendMessage(messageManager.getMessage("command-interface.status-can-start", mapOf("status" to if (canStart) "§a✓" else "§c✗")))
            
            if (!canStart) {
                if (totalPlayers < gameManager.getMinPlayers()) {
                    sender.sendMessage(messageManager.getMessage("status-detail.insufficient-players", mapOf("current" to totalPlayers, "required" to gameManager.getMinPlayers())))
                }
                if (hunters.isEmpty()) {
                    sender.sendMessage(messageManager.getMessage("status-detail.insufficient-hunters"))
                }
                if (runners.isEmpty()) {
                    sender.sendMessage(messageManager.getMessage("status-detail.insufficient-runners"))
                }
            }
        }
    }
    
    private fun handleSetHunter(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage("command-interface.admin-no-permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage("command-interface.sethunter-usage"))
            return
        }
        
        val playerName = args[1]
        if (playerName.isBlank()) {
            sender.sendMessage(messageManager.getMessage("admin.player-name-required"))
            return
        }
        
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null || !targetPlayer.isOnline) {
            sender.sendMessage(messageManager.getMessage("admin.player-not-found", mapOf("player" to playerName)))
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage(messageManager.getMessage("role.game-running"))
            return
        }
        
        gameManager.setPlayerRole(targetPlayer, PlayerRole.HUNTER)
        sender.sendMessage(messageManager.getMessage("command-interface.sethunter-success", mapOf("player" to targetPlayer.name)))
        targetPlayer.sendMessage(messageManager.getMessage(targetPlayer, "command-interface.sethunter-notify"))
    }
    
    private fun handleSetRunner(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage("command-interface.admin-no-permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage("command-interface.setrunner-usage"))
            return
        }
        
        val playerName = args[1]
        if (playerName.isBlank()) {
            sender.sendMessage(messageManager.getMessage("admin.player-name-required"))
            return
        }
        
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null || !targetPlayer.isOnline) {
            sender.sendMessage(messageManager.getMessage("admin.player-not-found", mapOf("player" to playerName)))
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage(messageManager.getMessage("role.game-running"))
            return
        }
        
        gameManager.setPlayerRole(targetPlayer, PlayerRole.RUNNER)
        sender.sendMessage(messageManager.getMessage("command-interface.setrunner-success", mapOf("player" to targetPlayer.name)))
        targetPlayer.sendMessage(messageManager.getMessage(targetPlayer, "command-interface.setrunner-notify"))
    }
    
    private fun handleSetSpectator(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage("command-interface.admin-no-permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage("command-interface.setspectator-usage"))
            return
        }
        
        val playerName = args[1]
        if (playerName.isBlank()) {
            sender.sendMessage(messageManager.getMessage("admin.player-name-required"))
            return
        }
        
        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null || !targetPlayer.isOnline) {
            sender.sendMessage(messageManager.getMessage("admin.player-not-found", mapOf("player" to playerName)))
            return
        }
        
        if (gameManager.getGameState() != GameState.WAITING) {
            sender.sendMessage(messageManager.getMessage("role.game-running"))
            return
        }
        
        gameManager.setPlayerRole(targetPlayer, PlayerRole.SPECTATOR)
        sender.sendMessage(messageManager.getMessage("command-interface.setspectator-success", mapOf("player" to targetPlayer.name)))
        targetPlayer.sendMessage(messageManager.getMessage(targetPlayer, "command-interface.setspectator-notify"))
    }
    
    private fun handleMinPlayers(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage("command-interface.admin-no-permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage("command-interface.minplayers-current", mapOf("count" to gameManager.getMinPlayers())))
            sender.sendMessage(messageManager.getMessage("command-interface.minplayers-change"))
            return
        }
        
        val countStr = args[1]
        if (countStr.isBlank()) {
            sender.sendMessage(messageManager.getMessage("admin.input-required"))
            return
        }
        
        val count = countStr.toIntOrNull()
        if (count == null) {
            sender.sendMessage(messageManager.getMessage("admin.invalid-number", mapOf("input" to countStr)))
            return
        }
        
        if (count < 2) {
            sender.sendMessage(messageManager.getMessage("admin.min-players-too-low"))
            return
        }
        
        if (count > 100) {
            sender.sendMessage(messageManager.getMessage("admin.min-players-too-high"))
            return
        }
        
        gameManager.setMinPlayers(count)
        sender.sendMessage(messageManager.getMessage("command-interface.minplayers-set", mapOf("count" to count)))
    }
    
    private fun handleReload(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage("command-interface.admin-no-permission"))
            return
        }
        
        val reloadType = if (args.size > 1) args[1].lowercase() else "all"
        
        try {
            when (reloadType) {
                "config" -> {
                    sender.sendMessage(messageManager.getMessage("reload.config-start"))
                    gameManager.configManager.reloadConfig()
                    sender.sendMessage(messageManager.getMessage("reload.config-complete"))
                }
                "shop" -> {
                    sender.sendMessage(messageManager.getMessage("reload.shop-start"))
                    gameManager.getPlugin().getShopManager().reloadShopConfig()
                    sender.sendMessage(messageManager.getMessage("reload.shop-complete"))
                }
                "all" -> {
                    sender.sendMessage(messageManager.getMessage("reload.all-start"))
                    gameManager.configManager.reloadConfig()
                    gameManager.getPlugin().getShopManager().reloadShopConfig()
                    sender.sendMessage(messageManager.getMessage("reload.all-complete"))
                }
                else -> {
                    sender.sendMessage(messageManager.getMessage("reload.usage"))
                    return
                }
            }
            sender.sendMessage(messageManager.getMessage("reload.note"))
        } catch (e: Exception) {
            sender.sendMessage(messageManager.getMessage("reload.failed", mapOf("error" to (e.message ?: "Unknown error"))))
        }
    }
    
    private fun handleUI(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage("command-interface.admin-no-permission"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(messageManager.getMessage("ui-settings.usage"))
            return
        }
        
        when (args[1].lowercase()) {
            "status" -> {
                sender.sendMessage(messageManager.getMessage("ui-settings.header"))
                val scoreboardStatus = if (configManager.isScoreboardEnabled()) messageManager.getMessage("ui-settings.enabled") else messageManager.getMessage("ui-settings.disabled")
                val actionbarStatus = if (configManager.isActionBarEnabled()) messageManager.getMessage("ui-settings.enabled") else messageManager.getMessage("ui-settings.disabled")
                val bossbarStatus = if (configManager.isBossBarEnabled()) messageManager.getMessage("ui-settings.enabled") else messageManager.getMessage("ui-settings.disabled")
                val titleStatus = if (configManager.isTitleEnabled()) messageManager.getMessage("ui-settings.enabled") else messageManager.getMessage("ui-settings.disabled")
                sender.sendMessage(messageManager.getMessage("ui-settings.scoreboard", mapOf("status" to scoreboardStatus)))
                sender.sendMessage(messageManager.getMessage("ui-settings.actionbar", mapOf("status" to actionbarStatus)))
                sender.sendMessage(messageManager.getMessage("ui-settings.bossbar", mapOf("status" to bossbarStatus)))
                sender.sendMessage(messageManager.getMessage("ui-settings.title", mapOf("status" to titleStatus)))
            }
            "toggle" -> {
                sender.sendMessage(messageManager.getMessage("ui-settings.config-note"))
                sender.sendMessage(messageManager.getMessage("ui-settings.reload-note"))
            }
            else -> {
                sender.sendMessage(messageManager.getMessage("ui-settings.usage"))
            }
        }
    }
    
    private fun handleSpectate(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "command.player-only"))
            return
        }
        
        spectatorMenu.openMenu(sender)
    }
    
    private fun handleRoleMenu(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(messageManager.getMessage(null, "command.player-only"))
            return
        }
        
        roleSelectorMenu.openMenu(sender)
    }
    
    private fun handleReset(sender: CommandSender) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage("command-interface.admin-no-permission"))
            return
        }
        
        if (gameManager.getGameState() != GameState.ENDED) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "command.reset-only-ended"))
            return
        }
        
        gameManager.forceReset()
        sender.sendMessage(messageManager.getMessage(sender as? Player, "command.reset-success"))
    }
    
    private fun handleValidateMessages(sender: CommandSender) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "command.no-permission"))
            return
        }
        
        sender.sendMessage("§6[Manhunt] §eStarting message validation...")
        
        // Run validation in async to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(gameManager.getPlugin(), Runnable {
            try {
                val validator = com.hacklab.manhunt.utils.MessageValidator(
                    messageManager,
                    gameManager.getPlugin().dataFolder
                )
                
                val result = validator.validateMessages()
                
                // Send results back to sender on main thread
                Bukkit.getScheduler().runTask(gameManager.getPlugin(), Runnable {
                    sender.sendMessage("§6[Manhunt] §aValidation complete!")
                    
                    if (result.missingInJa.isNotEmpty() || result.missingInEn.isNotEmpty()) {
                        sender.sendMessage("§c❌ Found missing message keys!")
                        
                        if (result.missingInJa.isNotEmpty()) {
                            sender.sendMessage("§e  Missing in ja.yml: ${result.missingInJa.size} keys")
                            result.missingInJa.take(5).forEach { key ->
                                sender.sendMessage("§7    - $key")
                            }
                            if (result.missingInJa.size > 5) {
                                sender.sendMessage("§7    ... and ${result.missingInJa.size - 5} more")
                            }
                        }
                        
                        if (result.missingInEn.isNotEmpty()) {
                            sender.sendMessage("§e  Missing in en.yml: ${result.missingInEn.size} keys")
                            result.missingInEn.take(5).forEach { key ->
                                sender.sendMessage("§7    - $key")
                            }
                            if (result.missingInEn.size > 5) {
                                sender.sendMessage("§7    ... and ${result.missingInEn.size - 5} more")
                            }
                        }
                        
                        sender.sendMessage("§eCheck console for detailed report.")
                    } else {
                        sender.sendMessage("§a✅ All message keys are properly defined!")
                    }
                })
            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(gameManager.getPlugin(), Runnable {
                    sender.sendMessage("§c[Manhunt] Error during validation: ${e.message}")
                    e.printStackTrace()
                })
            }
        })
    }
    
    private fun handleRespawnTime(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "command.no-permission"))
            return
        }
        
        if (args.size < 3) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.respawntime-usage"))
            return
        }
        
        val targetName = args[1]
        val targetPlayer = Bukkit.getPlayer(targetName)
        
        if (targetPlayer == null || !targetPlayer.isOnline) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.player-not-found", mapOf("player" to targetName)))
            return
        }
        
        val seconds = args[2].toIntOrNull()
        if (seconds == null || seconds < 0) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.invalid-time"))
            return
        }
        
        // GameManagerに個別のリスポン時間設定メソッドを追加
        gameManager.setCustomRespawnTime(targetPlayer, seconds)
        
        sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.respawntime-set", 
            mapOf("player" to targetPlayer.name, "time" to seconds)))
        
        // 対象プレイヤーにも通知
        targetPlayer.sendMessage(messageManager.getMessage(targetPlayer, "admin.respawntime-changed", 
            mapOf("time" to seconds)))
    }
    
    private fun handleGive(sender: CommandSender, args: Array<out String>) {
        // 管理者権限チェック
        if (!sender.hasPermission("manhunt.admin")) {
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.admin-no-permission"))
            return
        }
        
        // 引数チェック
        if (args.size < 3) {
            sender.sendMessage(messageManager.getMessage(sender, "admin.give-usage"))
            return
        }
        
        // プレイヤー取得
        val targetName = args[1]
        val targetPlayer = Bukkit.getPlayer(targetName)
        if (targetPlayer == null) {
            sender.sendMessage(messageManager.getMessage(sender, "admin.player-not-found", 
                mapOf("player" to targetName)))
            return
        }
        
        // 金額パース
        val amountStr = args[2]
        val amount = try {
            amountStr.toInt()
        } catch (e: NumberFormatException) {
            sender.sendMessage(messageManager.getMessage(sender, "admin.invalid-amount"))
            return
        }
        
        // 金額の妥当性チェック
        if (amount <= 0) {
            sender.sendMessage(messageManager.getMessage(sender, "admin.amount-must-be-positive"))
            return
        }
        
        val maxBalance = gameManager.getPlugin().getConfigManager().getCurrencyConfig().maxBalance
        if (amount > maxBalance) {
            sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.amount-too-large", 
                mapOf("max" to maxBalance)))
            return
        }
        
        // お金を付与
        val economyManager = gameManager.getPlugin().getEconomyManager()
        val unit = economyManager.getCurrencyUnit()
        
        // 管理者からの付与用の特別な理由を作成
        economyManager.addMoney(targetPlayer, amount, com.hacklab.manhunt.economy.EarnReason.AdminGrant(sender.name))
        
        // 送信者に通知
        sender.sendMessage(messageManager.getMessage(sender as? Player, "admin.give-success", 
            mapOf(
                "amount" to amount,
                "unit" to unit,
                "player" to targetPlayer.name,
                "balance" to economyManager.getBalance(targetPlayer)
            )))
        
        // 受信者に通知
        targetPlayer.sendMessage(messageManager.getMessage(targetPlayer, "economy.currency.received-from-admin", 
            mapOf(
                "amount" to amount,
                "unit" to unit,
                "admin" to sender.name
            )))
    }
    
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-commands-header"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-role-change"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-role-menu"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-compass-activate"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-status-check"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-spectate-menu"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-note"))
        sender.sendMessage("")
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-other-commands"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-teamchat"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-position"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-shop-open"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-shop-balance"))
        sender.sendMessage("")
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-virtual-compass"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-compass-usage"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-compass-display"))
        sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-compass-benefits"))
        
        if (sender.hasPermission("manhunt.admin")) {
            sender.sendMessage("")
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-commands"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-start"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-stop"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-sethunter"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-setrunner"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-setspectator"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-minplayers"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-reload"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-ui"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-respawntime"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-reset"))
            sender.sendMessage(messageManager.getMessage(sender, "command-interface.help-admin-give"))
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
                    val subcommands = mutableListOf("role", "roles", "compass", "status", "spectate", "help")
                    if (sender.hasPermission("manhunt.admin")) {
                        subcommands.addAll(listOf("start", "stop", "end", "sethunter", "setrunner", "setspectator", "minplayers", "ui", "reload", "respawntime", "reset", "validate-messages", "diagnose", "give"))
                    }
                    val input = args.getOrNull(0)?.lowercase() ?: ""
                    subcommands.filter { it.startsWith(input) }
                }
                2 -> {
                    val subcommand = args.getOrNull(0)?.lowercase() ?: return emptyList()
                    val input = args.getOrNull(1)?.lowercase() ?: ""
                    when (subcommand) {
                        "role" -> listOf("runner", "hunter", "spectator").filter { it.startsWith(input) }
                        "sethunter", "setrunner", "setspectator" -> {
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
                        "respawntime" -> {
                            if (sender.hasPermission("manhunt.admin")) {
                                Bukkit.getOnlinePlayers().mapNotNull { it?.name }.filter { it.lowercase().startsWith(input) }
                            } else {
                                emptyList()
                            }
                        }
                        "give" -> {
                            if (sender.hasPermission("manhunt.admin")) {
                                Bukkit.getOnlinePlayers().mapNotNull { it?.name }.filter { it.lowercase().startsWith(input) }
                            } else {
                                emptyList()
                            }
                        }
                        "validate-messages" -> emptyList()
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