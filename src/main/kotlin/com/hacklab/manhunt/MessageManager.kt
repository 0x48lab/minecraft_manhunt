package com.hacklab.manhunt

import org.bukkit.entity.Player
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*

class MessageManager(private val plugin: Main) {
    
    private val messages = mutableMapOf<String, Map<String, String>>()
    private val playerLanguages = mutableMapOf<UUID, String>()
    private var defaultLanguage = "en"
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
        defaultLanguage = config.getString("language.default", "en") ?: "en"
        perPlayerEnabled = config.getBoolean("language.per-player", true)
        
        plugin.logger.info("Loading language configuration: default=$defaultLanguage, per-player=$perPlayerEnabled")
        
        // サポートされていない言語の場合はデフォルトに戻す
        if (!SUPPORTED_LANGUAGES.contains(defaultLanguage)) {
            plugin.logger.warning("Unsupported default language: $defaultLanguage. Using 'en' instead.")
            defaultLanguage = "en"
        }
        
        plugin.logger.info("Final default language: $defaultLanguage")
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
        plugin.logger.info("Creating default message file for language: $language at ${file.absolutePath}")
        
        // 親ディレクトリが存在することを確認
        file.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
                plugin.logger.info("Created parent directory: ${parent.absolutePath}")
            }
        }
        
        // リソースからデフォルトファイルをコピー
        val resourcePath = "messages/$language.yml"
        plugin.getResource(resourcePath)!!.use { inputStream ->
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        plugin.logger.info("Copied default message file from resources: $resourcePath")
    }
    
    
    
    fun getMessage(player: Player?, key: String, vararg args: Any): String {
        val language = getPlayerLanguage(player)
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
    
    fun getMessage(sender: CommandSender?, key: String, vararg args: Any): String {
        return getMessage(sender as? Player, key, *args)
    }
    
    fun getMessage(key: String, vararg args: Any): String {
        val message = messages[defaultLanguage]?.get(key)
            ?: run {
                plugin.logger.warning("Missing message key: $key for default language: $defaultLanguage")
                plugin.logger.warning("Available languages: ${messages.keys}")
                plugin.logger.warning("Available keys for $defaultLanguage: ${messages[defaultLanguage]?.keys?.take(5)}")
                "§c[Missing message: $key]"
            }
        
        return formatMessage(message, *args)
    }
    
    fun getMessageList(key: String): List<String> {
        // YAMLのリスト形式のメッセージを取得
        val messagesDir = File(plugin.dataFolder, MESSAGES_FOLDER)
        val messageFile = File(messagesDir, "$defaultLanguage.yml")
        
        if (messageFile.exists()) {
            try {
                val config = YamlConfiguration.loadConfiguration(messageFile)
                return config.getStringList(key)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load list message key: $key")
            }
        }
        
        return emptyList()
    }
    
    fun getMessageList(player: Player?, key: String): List<String> {
        val language = getPlayerLanguage(player)
        val messagesDir = File(plugin.dataFolder, MESSAGES_FOLDER)
        val messageFile = File(messagesDir, "$language.yml")
        
        if (messageFile.exists()) {
            try {
                val config = YamlConfiguration.loadConfiguration(messageFile)
                return config.getStringList(key)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load list message key: $key for language: $language")
            }
        }
        
        return emptyList()
    }
    
    private fun formatMessage(message: String, vararg args: Any): String {
        var result = message
        
        // プレースホルダーの置換
        for (i in args.indices) {
            result = result.replace("{$i}", args[i].toString())
        }
        
        // 名前付きプレースホルダーの置換（マップで渡された場合）
        if (args.size == 1 && args[0] is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
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