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
    private lateinit var roleSelectorMenu: RoleSelectorMenu
    private lateinit var teamChatCommand: TeamChatCommand
    private lateinit var positionShareCommand: PositionShareCommand
    private lateinit var buddySystem: BuddySystem
    private lateinit var buddyCommand: BuddyCommand
    private lateinit var spawnManager: SpawnManagerSimple
    private lateinit var warpCommand: WarpCommand
    private lateinit var proximityTimeTracker: ProximityTimeTracker
    private lateinit var guideBookManager: GuideBookManager
    
    // Economy & Shop
    private lateinit var economyManager: EconomyManager
    private lateinit var currencyConfig: CurrencyConfig
    private lateinit var currencyTracker: CurrencyTracker
    private lateinit var shopManager: ShopManager
    private lateinit var shopCommand: ShopCommand
    private lateinit var shopListener: ShopListener

    override fun onEnable() {
        // Ensure data folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }
        
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
        gameManager.initialize() // 統計とリザルトシステムを初期化
        spawnManager = SpawnManagerSimple(this, gameManager, configManager)
        gameManager.setSpawnManager(spawnManager) // SpawnManagerをGameManagerに設定
        compassTracker = CompassTracker(this, gameManager, configManager, messageManager)
        guideBookManager = GuideBookManager(this)
        uiManager = UIManager(this, gameManager, configManager)
        
        // Initialize economy & shop
        economyManager = EconomyManager(this)
        currencyConfig = configManager.getCurrencyConfig()
        currencyTracker = CurrencyTracker(this, economyManager, currencyConfig)
        shopManager = ShopManager(this, economyManager)
        shopCommand = ShopCommand(this, shopManager, economyManager, messageManager)
        shopListener = ShopListener(this, shopManager, currencyTracker)
        
        spectatorMenu = SpectatorMenu(gameManager, messageManager)
        roleSelectorMenu = RoleSelectorMenu(gameManager, messageManager)
        eventListener = EventListener(this, gameManager, uiManager, messageManager, roleSelectorMenu)
        teamChatCommand = TeamChatCommand(gameManager, messageManager)
        positionShareCommand = PositionShareCommand(gameManager, messageManager)
        buddySystem = BuddySystem(this, gameManager, messageManager)
        buddyCommand = BuddyCommand(this, gameManager, buddySystem, messageManager)
        warpCommand = WarpCommand(gameManager, messageManager, economyManager)
        proximityTimeTracker = ProximityTimeTracker(this, gameManager, messageManager)
        
        // Register commands
        val manhuntCommand = ManhuntCommand(gameManager, compassTracker, spectatorMenu, messageManager, roleSelectorMenu)
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
        
        // Register buddy command
        getCommand("buddy")?.setExecutor(buddyCommand)
        getCommand("buddy")?.tabCompleter = buddyCommand
        
        // Register warp command
        getCommand("warp")?.setExecutor(warpCommand)
        getCommand("warp")?.tabCompleter = warpCommand
        
        // Register events
        server.pluginManager.registerEvents(eventListener, this)
        server.pluginManager.registerEvents(spectatorMenu, this)
        server.pluginManager.registerEvents(roleSelectorMenu, this)
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
    fun getBuddySystem(): BuddySystem = buddySystem
    fun getProximityTimeTracker(): ProximityTimeTracker = proximityTimeTracker
    fun getGuideBookManager(): GuideBookManager = guideBookManager

    override fun onDisable() {
        compassTracker.stopTracking()
        uiManager.stopDisplaySystem()
        spectatorMenu.cleanup()
        roleSelectorMenu.cleanup()
        
        // ProximityTimeTrackerを停止
        try {
            proximityTimeTracker.stopTracking()
        } catch (e: Exception) {
            logger.warning("ProximityTimeTrackerの停止でエラー: ${e.message}")
        }
        
        // ゲーム結果マネージャーのクリーンアップ
        try {
            gameManager.cleanup()
        } catch (e: Exception) {
            logger.warning("ゲームマネージャーのクリーンアップでエラー: ${e.message}")
        }
        
        logger.info("Manhunt プラグインが無効になりました。")
    }
}
