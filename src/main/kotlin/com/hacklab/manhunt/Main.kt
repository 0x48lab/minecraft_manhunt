package com.hacklab.manhunt

import com.hacklab.manhunt.economy.CurrencyConfig
import com.hacklab.manhunt.economy.CurrencyTracker
import com.hacklab.manhunt.economy.EconomyManager
import com.hacklab.manhunt.shop.ShopCommand
import com.hacklab.manhunt.shop.ShopListener
import com.hacklab.manhunt.shop.ShopManager
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    
    private lateinit var configManager: ConfigManager
    private lateinit var messageManager: MessageManager
    private lateinit var gameManager: GameManager
    private lateinit var compassTracker: CompassTracker
    private lateinit var eventListener: EventListener
    private lateinit var uiManager: UIManager
    private lateinit var spectatorMenu: SpectatorMenu
    private lateinit var teamChatCommand: TeamChatCommand
    private lateinit var positionShareCommand: PositionShareCommand
    
    // Economy & Shop
    private lateinit var economyManager: EconomyManager
    private lateinit var currencyConfig: CurrencyConfig
    private lateinit var currencyTracker: CurrencyTracker
    private lateinit var shopManager: ShopManager
    private lateinit var shopCommand: ShopCommand
    private lateinit var shopListener: ShopListener

    override fun onEnable() {
        // Save default config
        saveDefaultConfig()
        
        // Initialize config manager
        configManager = ConfigManager(this)
        configManager.validateAndFixConfig()
        
        // Initialize message manager
        messageManager = MessageManager(this)
        messageManager.initialize()
        
        // Initialize managers
        gameManager = GameManager(this, configManager, messageManager)
        compassTracker = CompassTracker(this, gameManager, configManager, messageManager)
        uiManager = UIManager(this, gameManager, configManager)
        
        // Initialize economy & shop
        economyManager = EconomyManager(this)
        currencyConfig = configManager.getCurrencyConfig()
        currencyTracker = CurrencyTracker(this, economyManager, currencyConfig)
        shopManager = ShopManager(this, economyManager)
        shopCommand = ShopCommand(this, shopManager, economyManager, messageManager)
        shopListener = ShopListener(this, shopManager, currencyTracker)
        
        eventListener = EventListener(gameManager, uiManager, messageManager)
        spectatorMenu = SpectatorMenu(gameManager)
        teamChatCommand = TeamChatCommand(gameManager, messageManager)
        positionShareCommand = PositionShareCommand(gameManager, messageManager)
        
        // Register commands
        val manhuntCommand = ManhuntCommand(gameManager, compassTracker, spectatorMenu, messageManager)
        getCommand("manhunt")?.setExecutor(manhuntCommand)
        getCommand("manhunt")?.tabCompleter = manhuntCommand
        
        // Register team chat command
        getCommand("r")?.setExecutor(teamChatCommand)
        getCommand("r")?.tabCompleter = teamChatCommand
        
        // Register position share command
        getCommand("pos")?.setExecutor(positionShareCommand)
        
        // Register shop command
        getCommand("shop")?.setExecutor(shopCommand)
        getCommand("shop")?.tabCompleter = shopCommand
        
        // Register events
        server.pluginManager.registerEvents(eventListener, this)
        server.pluginManager.registerEvents(spectatorMenu, this)
        server.pluginManager.registerEvents(shopListener, this)
        
        // Start UI system
        uiManager.startDisplaySystem()
        
        logger.info("Manhunt プラグインが有効になりました！")
    }
    
    fun getCompassTracker(): CompassTracker = compassTracker
    fun getConfigManager(): ConfigManager = configManager
    fun getMessageManager(): MessageManager = messageManager
    fun getUIManager(): UIManager = uiManager
    fun getSpectatorMenu(): SpectatorMenu = spectatorMenu
    fun getGameManager(): GameManager = gameManager
    fun getEconomyManager(): EconomyManager = economyManager
    fun getCurrencyTracker(): CurrencyTracker = currencyTracker
    fun getShopManager(): ShopManager = shopManager

    override fun onDisable() {
        compassTracker.stopTracking()
        uiManager.stopDisplaySystem()
        spectatorMenu.cleanup()
        logger.info("Manhunt プラグインが無効になりました。")
    }
}
