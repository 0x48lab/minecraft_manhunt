package com.hacklab.manhunt

import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    
    private lateinit var configManager: ConfigManager
    private lateinit var gameManager: GameManager
    private lateinit var compassTracker: CompassTracker
    private lateinit var eventListener: EventListener
    private lateinit var uiManager: UIManager

    override fun onEnable() {
        // Save default config
        saveDefaultConfig()
        
        // Initialize config manager
        configManager = ConfigManager(this)
        configManager.validateAndFixConfig()
        
        // Initialize managers
        gameManager = GameManager(this, configManager)
        compassTracker = CompassTracker(this, gameManager, configManager)
        uiManager = UIManager(this, gameManager, configManager)
        eventListener = EventListener(gameManager, uiManager)
        
        // Register commands
        getCommand("manhunt")?.setExecutor(ManhuntCommand(gameManager, compassTracker))
        getCommand("manhunt")?.tabCompleter = ManhuntCommand(gameManager, compassTracker)
        
        // Register events
        server.pluginManager.registerEvents(eventListener, this)
        
        // Start UI system
        uiManager.startDisplaySystem()
        
        logger.info("Manhunt プラグインが有効になりました！")
    }
    
    fun getCompassTracker(): CompassTracker = compassTracker
    fun getConfigManager(): ConfigManager = configManager
    fun getUIManager(): UIManager = uiManager

    override fun onDisable() {
        compassTracker.stopTracking()
        uiManager.stopDisplaySystem()
        logger.info("Manhunt プラグインが無効になりました。")
    }
}
