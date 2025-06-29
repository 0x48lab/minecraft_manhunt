package com.hacklab.manhunt

import org.bukkit.entity.Player
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*

class MessageManager(private val plugin: Main) {
    
    private val messages = mutableMapOf<String, Map<String, String>>()
    private val playerLanguages = mutableMapOf<UUID, String>()
    private var defaultLanguage = "ja"
    private var perPlayerEnabled = true
    
    companion object {
        private const val MESSAGES_FOLDER = "messages"
        private val SUPPORTED_LANGUAGES = setOf("ja", "en")
    }
    
    fun initialize() {
        plugin.logger.info("Initializing MessageManager...")
        loadConfiguration()
        loadMessageFiles()
        plugin.logger.info("MessageManager initialized with languages: ${messages.keys}")
        plugin.logger.info("Default language message count: ${messages[defaultLanguage]?.size ?: 0}")
    }
    
    private fun loadConfiguration() {
        val config = plugin.config
        defaultLanguage = config.getString("language.default", "ja") ?: "ja"
        perPlayerEnabled = config.getBoolean("language.per-player", true)
        
        // サポートされていない言語の場合はデフォルトに戻す
        if (!SUPPORTED_LANGUAGES.contains(defaultLanguage)) {
            plugin.logger.warning("Unsupported default language: $defaultLanguage. Using 'ja' instead.")
            defaultLanguage = "ja"
        }
    }
    
    private fun loadMessageFiles() {
        val messagesDir = File(plugin.dataFolder, MESSAGES_FOLDER)
        plugin.logger.info("Messages directory: ${messagesDir.absolutePath}")
        
        if (!messagesDir.exists()) {
            plugin.logger.info("Creating messages directory...")
            messagesDir.mkdirs()
            createDefaultMessageFiles()
        }
        
        messages.clear()
        
        for (language in SUPPORTED_LANGUAGES) {
            val messageFile = File(messagesDir, "$language.yml")
            plugin.logger.info("Loading message file: ${messageFile.absolutePath}")
            
            if (messageFile.exists()) {
                try {
                    val config = YamlConfiguration.loadConfiguration(messageFile)
                    messages[language] = loadMessagesFromConfig(config)
                    plugin.logger.info("Loaded ${messages[language]?.size ?: 0} messages for language: $language")
                } catch (e: Exception) {
                    plugin.logger.severe("Failed to load message file for language $language: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                plugin.logger.warning("Message file not found for language: $language, creating default...")
                createDefaultMessageFile(language, messageFile)
                // 作成後に再読み込み
                try {
                    val config = YamlConfiguration.loadConfiguration(messageFile)
                    messages[language] = loadMessagesFromConfig(config)
                    plugin.logger.info("Created and loaded ${messages[language]?.size ?: 0} messages for language: $language")
                } catch (e: Exception) {
                    plugin.logger.severe("Failed to load newly created message file for language $language: ${e.message}")
                }
            }
        }
    }
    
    private fun loadMessagesFromConfig(config: FileConfiguration): Map<String, String> {
        val result = mutableMapOf<String, String>()
        loadMessagesRecursive(config, "", result)
        return result
    }
    
    private fun loadMessagesRecursive(section: Any, prefix: String, result: MutableMap<String, String>) {
        val config = when (section) {
            is FileConfiguration -> section
            is org.bukkit.configuration.ConfigurationSection -> section
            else -> return
        }
        
        for (key in config.getKeys(false)) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = config.get(key)
            
            when {
                value is String -> result[fullKey] = value
                config.isConfigurationSection(key) -> {
                    val subSection = config.getConfigurationSection(key)
                    if (subSection != null) {
                        loadMessagesRecursive(subSection, fullKey, result)
                    }
                }
            }
        }
    }
    
    private fun createDefaultMessageFiles() {
        createDefaultMessageFile("ja", File(plugin.dataFolder, "$MESSAGES_FOLDER/ja.yml"))
        createDefaultMessageFile("en", File(plugin.dataFolder, "$MESSAGES_FOLDER/en.yml"))
    }
    
    private fun createDefaultMessageFile(language: String, file: File) {
        try {
            plugin.logger.info("Creating default message file for language: $language at ${file.absolutePath}")
            
            // 親ディレクトリが存在することを確認
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                    plugin.logger.info("Created parent directory: ${parent.absolutePath}")
                }
            }
            
            val config = YamlConfiguration()
            
            when (language) {
                "ja" -> {
                    plugin.logger.info("Adding Japanese messages...")
                    addJapaneseMessages(config)
                }
                "en" -> {
                    plugin.logger.info("Adding English messages...")
                    addEnglishMessages(config)
                }
            }
            
            config.save(file)
            plugin.logger.info("Successfully created default message file: ${file.name} (${file.length()} bytes)")
            
            // ファイルが実際に作成されたか確認
            if (!file.exists()) {
                plugin.logger.severe("File was not created despite successful save: ${file.absolutePath}")
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create default message file for $language: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun addJapaneseMessages(config: YamlConfiguration) {
        // ゲーム基本メッセージ
        config.set("game.start", "§6[Manhunt] ゲーム開始！")
        config.set("game.end", "§6[Manhunt] ゲーム終了！")
        config.set("game.reset", "§7ゲームがリセットされました。")
        config.set("game.hunter-win", "§c追う人の勝利！逃げる人を全員倒しました！")
        config.set("game.runner-win", "§a逃げる人の勝利！エンダードラゴンを倒しました！")
        
        // 役割メッセージ
        config.set("role.changed", "§a役割を{role}に変更しました！")
        config.set("role.runner", "逃げる人")
        config.set("role.hunter", "追う人")
        config.set("role.spectator", "観戦者")
        config.set("role.invalid", "§c無効な役割です。runner, hunter, spectator のいずれかを指定してください。")
        config.set("role.game-running", "§cゲーム開始後は役割を変更できません。")
        
        // コンパスメッセージ
        config.set("compass.activated", "§6[追跡コンパス] §a有効化されました！")
        config.set("compass.usage", "§e使い方: §7コンパスを持って右クリックでランナーを追跡")
        config.set("compass.slot-hint", "§e§l※コンパスが必要です（重複不可）")
        config.set("compass.hunter-only", "§c追う人のみがコンパスを使用できます！")
        config.set("compass.game-only", "§cゲーム進行中のみコンパスを使用できます。")
        config.set("compass.cooldown", "§cコンパスのクールダウン中... ({time}秒)")
        config.set("compass.no-target", "§c追跡対象のランナーが見つかりません。")
        config.set("compass.different-world", "§cランナーは別のワールドにいます: §e{world}")
        config.set("compass.tracking", "§6[コンパス] §e{player} §7- {distance}")
        config.set("compass.target-switched", "§b[ターゲット切り替え] §7[{index}/{total}] §e{player} §7- {distance}")
        config.set("compass.hint", "§6[ヒント] §eコンパスを持って右クリックでランナーを追跡できます！")
        config.set("compass.actionbar-hint", "§e§lコンパス右クリックでランナーを追跡")
        config.set("compass.actionbar-use", "§e§lコンパス右クリックで追跡開始")
        config.set("compass.title-activated", "§6§l仮想コンパス")
        config.set("compass.subtitle-activated", "§e右クリックで追跡開始")
        
        // 観戦メニューメッセージ
        config.set("spectate.menu-title", "§6観戦メニュー")
        config.set("spectate.spectator-only", "§c観戦者のみがこのメニューを使用できます。")
        config.set("spectate.teleported", "§a{player}にテレポートしました！")
        config.set("spectate.player-offline", "§cそのプレイヤーはオンラインではありません。")
        
        // コマンドメッセージ
        config.set("command.player-only", "§cプレイヤーのみが実行できるコマンドです。")
        config.set("command.no-permission", "§cこのコマンドを実行する権限がありません。")
        config.set("command.unknown", "§c不明なコマンドです。/manhunt help で使用法を確認してください。")
        config.set("command.usage", "§c使用法: {usage}")
        
        // ゲーム状況メッセージ
        config.set("status.header", "§6=== Manhunt ゲーム状況 ===")
        config.set("status.state", "§eゲーム状態: {state}")
        config.set("status.min-players", "§e最小プレイヤー数: {count}")
        config.set("status.runners", "§a逃げる人: {count}人 {players}")
        config.set("status.hunters", "§c追う人: {count}人 {players}")
        config.set("status.spectators", "§7観戦者: {count}人 {players}")
        config.set("status.total-players", "§e総プレイヤー数: {count}")
        config.set("status.can-start", "§e自動開始可能: {status}")
        
        // プレイヤー参加/退出メッセージ
        config.set("join.game-running", "§7ゲーム進行中のため、観戦者として参加しました。")
        config.set("join.next-game", "§e次回のゲームから役割を選択できます。")
        config.set("join.welcome", "§6[Manhunt] ゲームに参加しました！")
        config.set("join.role-select", "§e/manhunt role <runner|hunter> で役割を選択してください。")
        config.set("join.spectator", "§7観戦者として参加しました。")
        
        // 近接警告メッセージ
        config.set("proximity.level-1", "§c§l[警告] 追う人が1チャンク以内にいます！")
        config.set("proximity.level-2", "§6§l[警告] 追う人が2チャンク以内にいます！")
        config.set("proximity.level-3", "§e§l[警告] 追う人が3チャンク以内にいます！")
        
        // UI表示メッセージ
        config.set("ui.hunter-mode", "🗡 ハンターモード | 最寄りターゲット: {target} ({distance}m)")
        config.set("ui.runner-mode", "🏃 ランナーモード | エンダードラゴンを倒そう！")
        config.set("ui.spectator-mode", "👁 観戦モード | ゲームを観戦中...")
        
        // ヘルプメッセージ
        config.set("help.header", "§6=== Manhunt コマンド ===")
        config.set("help.role", "§e/manhunt role <runner|hunter|spectator> - 役割変更")
        config.set("help.compass", "§e/manhunt compass - 仮想追跡コンパスを有効化")
        config.set("help.status", "§e/manhunt status - ゲーム状況確認")
        config.set("help.spectate", "§e/manhunt spectate - 観戦メニューを開く（観戦者のみ）")
        config.set("help.note", "§7※ サーバー参加時に自動的にゲームに参加します")
        config.set("help.virtual-compass", "§b=== 仮想コンパスの使い方 ===")
        config.set("help.compass-usage", "§7• 空手で右クリック = 最寄りランナーを追跡")
        config.set("help.compass-display", "§7• パーティクルと矢印で方向を表示")
        config.set("help.compass-benefits", "§7• アイテムドロップや重複の心配なし")
        config.set("help.admin-header", "§c=== 管理者コマンド ===")
    }
    
    private fun addEnglishMessages(config: YamlConfiguration) {
        // Game basic messages
        config.set("game.start", "§6[Manhunt] Game Started!")
        config.set("game.end", "§6[Manhunt] Game Ended!")
        config.set("game.reset", "§7Game has been reset.")
        config.set("game.hunter-win", "§cHunters Win! All runners have been eliminated!")
        config.set("game.runner-win", "§aRunners Win! The Ender Dragon has been defeated!")
        
        // Role messages
        config.set("role.changed", "§aRole changed to {role}!")
        config.set("role.runner", "Runner")
        config.set("role.hunter", "Hunter")
        config.set("role.spectator", "Spectator")
        config.set("role.invalid", "§cInvalid role. Please specify runner, hunter, or spectator.")
        config.set("role.game-running", "§cCannot change role after the game has started.")
        
        // Compass messages
        config.set("compass.activated", "§6[Tracking Compass] §aActivated!")
        config.set("compass.usage", "§eUsage: §7Hold compass and right-click to track runners")
        config.set("compass.slot-hint", "§e§l※Compass required (no duplicates allowed)")
        config.set("compass.hunter-only", "§cOnly hunters can use the compass!")
        config.set("compass.game-only", "§cCompass can only be used during the game.")
        config.set("compass.cooldown", "§cCompass on cooldown... ({time}s)")
        config.set("compass.no-target", "§cNo runner targets found.")
        config.set("compass.different-world", "§cRunner is in a different world: §e{world}")
        config.set("compass.tracking", "§6[Compass] §e{player} §7- {distance}")
        config.set("compass.target-switched", "§b[Target Switch] §7[{index}/{total}] §e{player} §7- {distance}")
        config.set("compass.hint", "§6[Hint] §eHold compass and right-click to track runners!")
        config.set("compass.actionbar-hint", "§e§lCompass right-click to track runners")
        config.set("compass.actionbar-use", "§e§lCompass right-click to start tracking")
        config.set("compass.title-activated", "§6§lVirtual Compass")
        config.set("compass.subtitle-activated", "§eRight-click to start tracking")
        
        // Spectate menu messages
        config.set("spectate.menu-title", "§6Spectator Menu")
        config.set("spectate.spectator-only", "§cOnly spectators can use this menu.")
        config.set("spectate.teleported", "§aTeleported to {player}!")
        config.set("spectate.player-offline", "§cThat player is not online.")
        
        // Command messages
        config.set("command.player-only", "§cThis command can only be executed by players.")
        config.set("command.no-permission", "§cYou don't have permission to execute this command.")
        config.set("command.unknown", "§cUnknown command. Use /manhunt help to see usage.")
        config.set("command.usage", "§cUsage: {usage}")
        
        // Game status messages
        config.set("status.header", "§6=== Manhunt Game Status ===")
        config.set("status.state", "§eGame State: {state}")
        config.set("status.min-players", "§eMinimum Players: {count}")
        config.set("status.runners", "§aRunners: {count} players {players}")
        config.set("status.hunters", "§cHunters: {count} players {players}")
        config.set("status.spectators", "§7Spectators: {count} players {players}")
        config.set("status.total-players", "§eTotal Players: {count}")
        config.set("status.can-start", "§eCan Auto-start: {status}")
        
        // Player join/quit messages
        config.set("join.game-running", "§7Joined as spectator because the game is in progress.")
        config.set("join.next-game", "§eYou can select a role for the next game.")
        config.set("join.welcome", "§6[Manhunt] Joined the game!")
        config.set("join.role-select", "§eUse /manhunt role <runner|hunter> to select your role.")
        config.set("join.spectator", "§7Joined as spectator.")
        
        // Proximity warning messages
        config.set("proximity.level-1", "§c§l[WARNING] Hunter within 1 chunk!")
        config.set("proximity.level-2", "§6§l[WARNING] Hunter within 2 chunks!")
        config.set("proximity.level-3", "§e§l[WARNING] Hunter within 3 chunks!")
        
        // UI display messages
        config.set("ui.hunter-mode", "🗡 Hunter Mode | Nearest Target: {target} ({distance}m)")
        config.set("ui.runner-mode", "🏃 Runner Mode | Defeat the Ender Dragon!")
        config.set("ui.spectator-mode", "👁 Spectator Mode | Watching the game...")
        
        // Help messages
        config.set("help.header", "§6=== Manhunt Commands ===")
        config.set("help.role", "§e/manhunt role <runner|hunter|spectator> - Change role")
        config.set("help.compass", "§e/manhunt compass - Activate virtual tracking compass")
        config.set("help.status", "§e/manhunt status - Check game status")
        config.set("help.spectate", "§e/manhunt spectate - Open spectator menu (spectators only)")
        config.set("help.note", "§7※ You automatically join the game when entering the server")
        config.set("help.virtual-compass", "§b=== Virtual Compass Usage ===")
        config.set("help.compass-usage", "§7• Right-click with empty hand = Track nearest runner")
        config.set("help.compass-display", "§7• Particles and arrows show direction")
        config.set("help.compass-benefits", "§7• No item drops or duplication issues")
        config.set("help.admin-header", "§c=== Admin Commands ===")
    }
    
    fun getMessage(player: Player?, key: String, vararg args: Any): String {
        val language = getPlayerLanguage(player)
        return getMessage(language, key, *args)
    }
    
    fun getMessage(language: String, key: String, vararg args: Any): String {
        val message = messages[language]?.get(key) 
            ?: messages[defaultLanguage]?.get(key)
            ?: run {
                plugin.logger.warning("Missing message key: $key for language: $language (default: $defaultLanguage)")
                plugin.logger.warning("Available languages: ${messages.keys}")
                plugin.logger.warning("Available keys for $defaultLanguage: ${messages[defaultLanguage]?.keys?.take(5)}")
                "§c[Missing message: $key]"
            }
        
        return formatMessage(message, *args)
    }
    
    private fun formatMessage(message: String, vararg args: Any): String {
        var result = message
        
        // プレースホルダーの置換
        for (i in args.indices) {
            result = result.replace("{$i}", args[i].toString())
        }
        
        // 名前付きプレースホルダーの置換（マップで渡された場合）
        if (args.size == 1 && args[0] is Map<*, *>) {
            val placeholders = args[0] as Map<String, Any>
            for ((key, value) in placeholders) {
                result = result.replace("{$key}", value.toString())
            }
        }
        
        return result
    }
    
    fun getPlayerLanguage(player: Player?): String {
        if (!perPlayerEnabled || player == null) {
            return defaultLanguage
        }
        
        return playerLanguages[player.uniqueId] ?: defaultLanguage
    }
    
    fun setPlayerLanguage(player: Player, language: String) {
        if (!perPlayerEnabled) return
        
        if (SUPPORTED_LANGUAGES.contains(language)) {
            playerLanguages[player.uniqueId] = language
            player.sendMessage(getMessage(player, "language.changed", language))
        } else {
            player.sendMessage(getMessage(player, "language.unsupported", language))
        }
    }
    
    fun reload() {
        loadConfiguration()
        loadMessageFiles()
        plugin.logger.info("Message system reloaded")
    }
    
    fun getSupportedLanguages(): Set<String> {
        return SUPPORTED_LANGUAGES.toSet()
    }
}