package com.hacklab.manhunt

import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    
    private lateinit var configManager: ConfigManager
    private lateinit var messageManager: MessageManager
    private lateinit var gameManager: GameManager
    private lateinit var compassTracker: CompassTracker
    private lateinit var eventListener: EventListener
    private lateinit var uiManager: UIManager
    private lateinit var spectatorMenu: SpectatorMenu

    override fun onEnable() {
        // Save default config
        saveDefaultConfig()
        
        // Initialize config manager
        configManager = ConfigManager(this)
        configManager.validateAndFixConfig()
        
        // Initialize message manager
        messageManager = MessageManager(this)
        messageManager.initialize()
        
        // Initialize managers (temporarily without MessageManager for other classes)
        gameManager = GameManager(this, configManager)
        compassTracker = CompassTracker(this, gameManager, configManager, messageManager)
        uiManager = UIManager(this, gameManager, configManager)
        eventListener = EventListener(gameManager, uiManager)
        spectatorMenu = SpectatorMenu(gameManager)
        
        // Register commands
        val manhuntCommand = ManhuntCommand(gameManager, compassTracker, spectatorMenu)
        getCommand("manhunt")?.setExecutor(manhuntCommand)
        getCommand("manhunt")?.tabCompleter = manhuntCommand
        
        // Register events
        server.pluginManager.registerEvents(eventListener, this)
        server.pluginManager.registerEvents(spectatorMenu, this)
        
        // Start UI system
        uiManager.startDisplaySystem()
        
        logger.info("Manhunt プラグインが有効になりました！")
    }
    
    fun getCompassTracker(): CompassTracker = compassTracker
    fun getConfigManager(): ConfigManager = configManager
    fun getMessageManager(): MessageManager = messageManager
    fun getUIManager(): UIManager = uiManager
    fun getSpectatorMenu(): SpectatorMenu = spectatorMenu

    override fun onDisable() {
        compassTracker.stopTracking()
        uiManager.stopDisplaySystem()
        spectatorMenu.cleanup()
        logger.info("Manhunt プラグインが無効になりました。")
    }
}
