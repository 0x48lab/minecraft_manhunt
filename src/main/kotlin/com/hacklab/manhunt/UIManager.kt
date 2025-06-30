package com.hacklab.manhunt

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.*

/**
 * ゲーム状況の視覚的表示を管理するUIマネージャー
 * Scoreboard、ActionBar、BossBar、Titleを統合管理
 */
class UIManager(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val configManager: ConfigManager
) {
    
    private val messageManager: MessageManager
        get() = plugin.getMessageManager()
    
    
    private var scoreboard: Scoreboard? = null
    private var objective: Objective? = null
    private var updateTask: BukkitTask? = null
    private var actionBarTask: BukkitTask? = null
    
    // BossBar管理
    private val playerBossBars = mutableMapOf<Player, BossBar>()
    
    // ActionBar表示用の状態
    private var currentActionBarMessage = ""
    
    fun startDisplaySystem() {
        if (configManager.isScoreboardEnabled()) {
            setupScoreboard()
            startScoreboardUpdates()
        }
        if (configManager.isActionBarEnabled()) {
            startActionBarUpdates()
        }
    }
    
    fun stopDisplaySystem() {
        updateTask?.cancel()
        actionBarTask?.cancel()
        clearAllBossBars()
        clearScoreboard()
    }
    
    // ======== Scoreboard システム ========
    
    private fun setupScoreboard() {
        val manager = Bukkit.getScoreboardManager() ?: return
        scoreboard = manager.newScoreboard
        
        objective = scoreboard?.registerNewObjective(
            "manhunt", 
            "dummy", 
            messageManager.getMessage("ui.scoreboard.title")
        )
        objective?.displaySlot = DisplaySlot.SIDEBAR
    }
    
    private fun startScoreboardUpdates() {
        updateTask?.cancel()
        updateTask = object : BukkitRunnable() {
            override fun run() {
                updateScoreboardForAllPlayers()
            }
        }.runTaskTimer(plugin, 0L, configManager.getScoreboardUpdateInterval())
    }
    
    private fun updateScoreboardForAllPlayers() {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) return
        
        clearScoreboardEntries()
        
        val gameState = gameManager.getGameState()
        val hunters = gameManager.getAllHunters().filter { it.isOnline }
        val runners = gameManager.getAllRunners().filter { it.isOnline }
        val spectators = gameManager.getAllSpectators().filter { it.isOnline }
        
        var line = 15
        
        // ゲーム状態表示
        addScoreboardLine("§r", line--) // 空行
        addScoreboardLine(messageManager.getMessage("ui.scoreboard.state", mapOf("state" to getGameStateDisplay(null, gameState))), line--)
        addScoreboardLine("§r ", line--) // 空行
        
        // ゲーム状態に応じた詳細情報
        if (gameState == GameState.RUNNING) {
            // ゲーム中：生存数・死亡数を表示
            val aliveHunters = hunters.filter { !it.isDead }
            val aliveRunners = runners.filter { !it.isDead }
            val deadHunters = hunters.filter { it.isDead }
            val deadRunners = runners.filter { it.isDead }
            
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.hunters-alive", mapOf("count" to aliveHunters.size)), line--)
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.hunters-dead", mapOf("count" to deadHunters.size)), line--)
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.runners-alive", mapOf("count" to aliveRunners.size)), line--)
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.runners-dead", mapOf("count" to deadRunners.size)), line--)
            addScoreboardLine("§r   ", line--) // 空行
        } else {
            // ゲーム開始前：プレイヤー数表示
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.hunters-total", mapOf("count" to hunters.size)), line--)
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.runners-total", mapOf("count" to runners.size)), line--)
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.spectators-total", mapOf("count" to spectators.size)), line--)
            addScoreboardLine("§r  ", line--) // 空行
        }
        
        // 待機中の場合
        if (gameState == GameState.WAITING) {
            val minPlayers = gameManager.getMinPlayers()
            val totalPlayers = hunters.size + runners.size
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.required-players", mapOf("current" to totalPlayers, "min" to minPlayers)), line--)
            addScoreboardLine("§r    ", line--) // 空行
        }
        
        // コマンド情報
        addScoreboardLine(messageManager.getMessage("ui.scoreboard.separator"), line--)
        addScoreboardLine(messageManager.getMessage("ui.scoreboard.help-command"), line--)
        addScoreboardLine(messageManager.getMessage("ui.scoreboard.help-text"), line--)
        
        // 各プレイヤーに個別のスコアボードを作成・適用
        onlinePlayers.forEach { player ->
            createPlayerScoreboard(player)
        }
    }
    
    private fun createPlayerScoreboard(player: Player) {
        // 各プレイヤー用の完全なスコアボードを作成
        val playerScoreboard = Bukkit.getScoreboardManager()?.newScoreboard ?: return
        val playerObjective = playerScoreboard.registerNewObjective("manhunt", "dummy", messageManager.getMessage(player, "ui.scoreboard.player-title"))
        playerObjective.displaySlot = DisplaySlot.SIDEBAR
        
        val gameState = gameManager.getGameState()
        val hunters = gameManager.getAllHunters().filter { it.isOnline }
        val runners = gameManager.getAllRunners().filter { it.isOnline }
        val spectators = gameManager.getAllSpectators().filter { it.isOnline }
        val role = gameManager.getPlayerRole(player)
        
        var line = 15
        
        // ゲーム状態表示
        addPlayerScoreboardLine(playerObjective, "§r", line--) // 空行
        addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.state", mapOf("state" to getGameStateDisplay(player, gameState))), line--)
        addPlayerScoreboardLine(playerObjective, "§r ", line--) // 空行
        
        // ゲーム状態に応じた詳細情報
        if (gameState == GameState.RUNNING) {
            // ゲーム中：生存数・死亡数を表示
            val aliveHunters = hunters.filter { !it.isDead }
            val aliveRunners = runners.filter { !it.isDead }
            val deadHunters = hunters.filter { it.isDead }
            val deadRunners = runners.filter { it.isDead }
            
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.hunters-alive", mapOf("count" to aliveHunters.size)), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.hunters-dead", mapOf("count" to deadHunters.size)), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.runners-alive", mapOf("count" to aliveRunners.size)), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.runners-dead", mapOf("count" to deadRunners.size)), line--)
            addPlayerScoreboardLine(playerObjective, "§r   ", line--) // 空行
            
            // 所持金表示（ゲーム中のみ）
            if (role != null && role != PlayerRole.SPECTATOR) {
                val balance = plugin.getEconomyManager().getBalance(player)
                val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.balance", mapOf("balance" to balance, "unit" to unit)), line--)
                addPlayerScoreboardLine(playerObjective, "§r     ", line--) // 空行
            }
        } else {
            // ゲーム開始前：プレイヤー数表示
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.hunters-total", mapOf("count" to hunters.size)), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.runners-total", mapOf("count" to runners.size)), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.spectators-total", mapOf("count" to spectators.size)), line--)
            addPlayerScoreboardLine(playerObjective, "§r  ", line--) // 空行
        }
        
        
        // 待機中の場合は追加情報
        if (gameState == GameState.WAITING) {
            val minPlayers = gameManager.getMinPlayers()
            val totalPlayers = hunters.size + runners.size
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.required-players", mapOf("current" to totalPlayers, "min" to minPlayers)), line--)
            addPlayerScoreboardLine(playerObjective, "§r    ", line--) // 空行
        }
        
        // コマンド情報
        addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.separator"), line--)
        addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.help-command"), line--)
        addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.help-text"), line--)
        
        // プレイヤーリスト（Tabキー）表示を設定
        setupPlayerListDisplay(player, playerScoreboard)
        
        // プレイヤーにスコアボードを適用
        player.scoreboard = playerScoreboard
    }
    
    private fun setupPlayerListDisplay(viewer: Player, scoreboard: Scoreboard) {
        // プレイヤーリスト用のObjectiveを作成
        val playerListObjective = scoreboard.registerNewObjective("playerlist", "dummy", messageManager.getMessage(viewer, "ui.playerlist.title"))
        playerListObjective.displaySlot = DisplaySlot.PLAYER_LIST
        
        val viewerRole = gameManager.getPlayerRole(viewer)
        
        // 味方プレイヤーのみに対して表示を設定
        Bukkit.getOnlinePlayers().forEach { target ->
            if (target.world == viewer.world) {
                val targetRole = gameManager.getPlayerRole(target)
                
                if (target == viewer) {
                    // 自分自身を黄色で表示
                    val selfTeam = scoreboard.getTeam("self_${target.name}") ?: scoreboard.registerNewTeam("self_${target.name}")
                    selfTeam.color = org.bukkit.ChatColor.YELLOW
                    selfTeam.prefix = "⭐"
                    selfTeam.suffix = messageManager.getMessage(viewer, "ui.scoreboard.self-suffix")
                    selfTeam.addEntry(target.name)
                } else if (isAlly(viewerRole, targetRole)) {
                    // 味方同士のみ表示（同じ役割かつ観戦者以外）
                    val coordsText = getRelativeCoordinates(viewer, target)
                    
                    // チーム設定で名前の色を変更（味方は青色）
                    val teamName = "ally_${target.name}"
                    var team = scoreboard.getTeam(teamName)
                    if (team == null) {
                        team = scoreboard.registerNewTeam(teamName)
                        team.color = org.bukkit.ChatColor.BLUE
                    }
                    team.prefix = "💙"
                    team.suffix = " §7($coordsText)"
                    team.addEntry(target.name)
                }
            }
        }
    }
    
    private fun getRelativeCoordinates(viewer: Player, target: Player): String {
        return try {
            val deltaX = target.location.blockX - viewer.location.blockX
            val deltaY = target.location.blockY - viewer.location.blockY
            val deltaZ = target.location.blockZ - viewer.location.blockZ
            
            val xSign = if (deltaX >= 0) "+" else ""
            val ySign = if (deltaY >= 0) "+" else ""
            val zSign = if (deltaZ >= 0) "+" else ""
            
            "X:$xSign$deltaX Y:$ySign$deltaY Z:$zSign$deltaZ"
        } catch (e: Exception) {
            messageManager.getMessage(viewer, "ui.coordinate-error")
        }
    }
    
    
    private fun getTeamName(viewer: Player, target: Player, viewerRole: PlayerRole?, targetRole: PlayerRole?): String {
        return when {
            target == viewer -> "self"
            isAlly(viewerRole, targetRole) -> "ally"
            isEnemy(viewerRole, targetRole) -> "enemy"
            else -> "neutral"
        }
    }
    
    private fun getPlayerNameColor(viewer: Player, target: Player, viewerRole: PlayerRole?, targetRole: PlayerRole?): org.bukkit.ChatColor {
        return when {
            target == viewer -> org.bukkit.ChatColor.YELLOW // 自分：黄色
            isAlly(viewerRole, targetRole) -> org.bukkit.ChatColor.BLUE // 味方：青
            isEnemy(viewerRole, targetRole) -> org.bukkit.ChatColor.RED // 敵：赤
            else -> org.bukkit.ChatColor.GRAY // その他：灰色
        }
    }
    
    private fun isAlly(viewerRole: PlayerRole?, targetRole: PlayerRole?): Boolean {
        return viewerRole != null && targetRole != null && 
               viewerRole == targetRole && 
               viewerRole != PlayerRole.SPECTATOR
    }
    
    private fun isEnemy(viewerRole: PlayerRole?, targetRole: PlayerRole?): Boolean {
        return viewerRole != null && targetRole != null && 
               viewerRole != targetRole && 
               viewerRole != PlayerRole.SPECTATOR && 
               targetRole != PlayerRole.SPECTATOR
    }
    
    private fun addPlayerScoreboardLine(objective: Objective, text: String, score: Int) {
        val entry = if (text.length > 16) text.substring(0, 16) else text
        objective.getScore(entry).score = score
    }
    
    private fun addScoreboardLine(text: String, score: Int) {
        val entry = if (text.length > 16) text.substring(0, 16) else text
        objective?.getScore(entry)?.score = score
    }
    
    private fun clearScoreboardEntries() {
        objective?.let { obj ->
            obj.scoreboard?.getEntries()?.forEach { entry ->
                obj.scoreboard?.resetScores(entry)
            }
        }
    }
    
    private fun clearScoreboard() {
        Bukkit.getOnlinePlayers().forEach { player ->
            player.scoreboard = Bukkit.getScoreboardManager()?.mainScoreboard ?: return@forEach
        }
        scoreboard?.getObjective("manhunt")?.unregister()
    }
    
    // ======== ActionBar システム ========
    
    private fun startActionBarUpdates() {
        actionBarTask?.cancel()
        actionBarTask = object : BukkitRunnable() {
            override fun run() {
                updateActionBarForAllPlayers()
            }
        }.runTaskTimer(plugin, 0L, configManager.getActionBarUpdateInterval())
    }
    
    private fun updateActionBarForAllPlayers() {
        val gameState = gameManager.getGameState()
        
        Bukkit.getOnlinePlayers().forEach { player ->
            val role = gameManager.getPlayerRole(player)
            val message = when {
                gameState == GameState.WAITING -> {
                    if (role == null) {
                        messageManager.getMessage(player, "ui.actionbar.join-game")
                    } else {
                        messageManager.getMessage(player, "ui.actionbar.waiting", mapOf("role" to getRoleDisplay(player, role)))
                    }
                }
                gameState == GameState.RUNNING && role != null -> {
                    when (role) {
                        PlayerRole.HUNTER -> {
                            val nearestRunner = findNearestRunner(player)
                            if (nearestRunner != null) {
                                val distance = try {
                                    val actualDistance = player.location.distance(nearestRunner.location).toInt()
                                    val minDistance = configManager.getMinimumDisplayDistance()
                                    if (actualDistance <= minDistance) minDistance else actualDistance
                                } catch (e: Exception) {
                                    -1
                                }
                                messageManager.getMessage(player, "ui.actionbar.hunter-with-target", mapOf("target" to nearestRunner.name, "distance" to distance))
                            } else {
                                messageManager.getMessage(player, "ui.actionbar.hunter-no-target")
                            }
                        }
                        PlayerRole.RUNNER -> {
                            messageManager.getMessage(player, "ui.actionbar.runner")
                        }
                        PlayerRole.SPECTATOR -> messageManager.getMessage(player, "ui.actionbar.spectator")
                    }
                }
                else -> messageManager.getMessage(player, "ui.actionbar.default")
            }
            
            sendActionBar(player, message)
        }
    }
    
    fun sendActionBar(player: Player, message: String) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(message))
        } catch (e: Exception) {
            // Spigot API対応していない場合のフォールバック
            // player.sendMessage(message) // チャットに表示
        }
    }
    
    // ======== BossBar システム ========
    
    fun showGameProgressBossBar(player: Player, title: String, progress: Double, color: BarColor = BarColor.BLUE) {
        if (!configManager.isBossBarEnabled()) return
        
        removeBossBar(player)
        
        val bossBar = Bukkit.createBossBar(title, color, BarStyle.SOLID)
        bossBar.progress = progress.coerceIn(0.0, 1.0)
        bossBar.addPlayer(player)
        bossBar.isVisible = true
        
        playerBossBars[player] = bossBar
    }
    
    fun updateBossBar(player: Player, title: String? = null, progress: Double? = null) {
        playerBossBars[player]?.let { bossBar ->
            title?.let { bossBar.setTitle(it) }
            progress?.let { bossBar.progress = it.coerceIn(0.0, 1.0) }
        }
    }
    
    fun removeBossBar(player: Player) {
        playerBossBars[player]?.let { bossBar ->
            bossBar.removeAll()
            playerBossBars.remove(player)
        }
    }
    
    private fun clearAllBossBars() {
        playerBossBars.values.forEach { it.removeAll() }
        playerBossBars.clear()
    }
    
    // ======== Title/Subtitle システム ========
    
    fun showTitle(player: Player, title: String, subtitle: String = "", fadeIn: Int = 10, stay: Int = 70, fadeOut: Int = 20) {
        if (!configManager.isTitleEnabled()) return
        
        try {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut)
        } catch (e: Exception) {
            // フォールバック: チャットメッセージ
            player.sendMessage("$title${if (subtitle.isNotEmpty()) " - $subtitle" else ""}")
        }
    }
    
    fun showGameStateChange(newState: GameState) {
        val color = when (newState) {
            GameState.STARTING -> BarColor.YELLOW
            GameState.RUNNING -> BarColor.GREEN
            GameState.ENDED -> BarColor.RED
            else -> return
        }
        
        Bukkit.getOnlinePlayers().forEach { player ->
            val (title, subtitle) = when (newState) {
                GameState.STARTING -> Pair(messageManager.getMessage(player, "ui.bossbar.starting.title"), messageManager.getMessage(player, "ui.bossbar.starting.subtitle"))
                GameState.RUNNING -> Pair(messageManager.getMessage(player, "ui.bossbar.running.title"), messageManager.getMessage(player, "ui.bossbar.running.subtitle"))
                GameState.ENDED -> Pair(messageManager.getMessage(player, "ui.bossbar.ended.title"), messageManager.getMessage(player, "ui.bossbar.ended.subtitle"))
                else -> return@forEach
            }
            
            showTitle(player, title, subtitle)
            if (newState == GameState.RUNNING) {
                showGameProgressBossBar(player, messageManager.getMessage(player, "ui.bossbar.progress"), 1.0, color)
            }
        }
    }
    
    // ======== 便利メソッド ========
    
    private fun getGameStateDisplay(player: Player?, state: GameState): String = when (state) {
        GameState.WAITING -> messageManager.getMessage(player, "ui.gamestate.waiting")
        GameState.STARTING -> messageManager.getMessage(player, "ui.gamestate.starting")
        GameState.RUNNING -> messageManager.getMessage(player, "ui.gamestate.running")
        GameState.ENDED -> messageManager.getMessage(player, "ui.gamestate.ended")
    }
    
    private fun getRoleDisplay(player: Player?, role: PlayerRole): String = when (role) {
        PlayerRole.HUNTER -> messageManager.getMessage(player, "ui.role-display.hunter")
        PlayerRole.RUNNER -> messageManager.getMessage(player, "ui.role-display.runner")
        PlayerRole.SPECTATOR -> messageManager.getMessage(player, "ui.role-display.spectator")
    }
    
    private fun findNearestRunner(hunter: Player): Player? {
        val hunterWorld = hunter.world ?: return null
        return gameManager.getAllRunners()
            .filter { it.isOnline && !it.isDead && it.world == hunterWorld }
            .minByOrNull { 
                try {
                    hunter.location.distance(it.location)
                } catch (e: Exception) {
                    Double.MAX_VALUE
                }
            }
    }
    
    private fun findNearestHunter(runner: Player): Player? {
        val runnerWorld = runner.world ?: return null
        return gameManager.getAllHunters()
            .filter { it.isOnline && !it.isDead && it.world == runnerWorld }
            .minByOrNull { 
                try {
                    runner.location.distance(it.location)
                } catch (e: Exception) {
                    Double.MAX_VALUE
                }
            }
    }
    
    // ======== プレイヤー参加/退出処理 ========
    
    fun onPlayerJoin(player: Player) {
        // 新規参加プレイヤーにスコアボード適用
        player.scoreboard = scoreboard ?: Bukkit.getScoreboardManager()?.mainScoreboard ?: return
        
        // ゲーム中の場合、状況を表示
        if (gameManager.getGameState() == GameState.RUNNING) {
            showGameProgressBossBar(player, messageManager.getMessage(player, "ui.bossbar.progress"), 1.0, BarColor.GREEN)
        }
    }
    
    fun onPlayerQuit(player: Player) {
        removeBossBar(player)
    }
    
    // ======== 即座更新メソッド ========
    
    /**
     * スコアボードを即座に更新する
     * プレイヤーの役割変更時などに呼び出す
     */
    fun updateScoreboardImmediately() {
        if (configManager.isScoreboardEnabled()) {
            updateScoreboardForAllPlayers()
        }
    }
    
    
    // ======== ヘルパーメソッド ========
    
    private fun getDeadRunnersCount(): Int {
        return try {
            gameManager.getDeadRunnersCount()
        } catch (e: Exception) {
            0
        }
    }
}