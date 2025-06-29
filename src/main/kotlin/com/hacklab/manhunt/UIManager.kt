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
            "§6§l🏃 MANHUNT GAME"
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
        addScoreboardLine("§f状態: ${getGameStateDisplay(gameState)}", line--)
        addScoreboardLine("§r ", line--) // 空行
        
        // ゲーム状態に応じた詳細情報
        if (gameState == GameState.RUNNING) {
            // ゲーム中：生存数・死亡数を表示
            val aliveHunters = hunters.filter { !it.isDead }
            val aliveRunners = runners.filter { !it.isDead }
            val deadHunters = hunters.filter { it.isDead }
            val deadRunners = runners.filter { it.isDead }
            
            addScoreboardLine("§c🗡 ハンター生存: §f${aliveHunters.size}", line--)
            addScoreboardLine("§c💀 ハンター死亡: §f${deadHunters.size}", line--)
            addScoreboardLine("§a🏃 ランナー生存: §f${aliveRunners.size}", line--)
            addScoreboardLine("§a💀 ランナー死亡: §f${deadRunners.size}", line--)
            addScoreboardLine("§r   ", line--) // 空行
        } else {
            // ゲーム開始前：プレイヤー数表示
            addScoreboardLine("§c🗡 ハンター: §f${hunters.size}", line--)
            addScoreboardLine("§a🏃 ランナー: §f${runners.size}", line--)
            addScoreboardLine("§7👁 観戦者: §f${spectators.size}", line--)
            addScoreboardLine("§r  ", line--) // 空行
        }
        
        // 待機中の場合
        if (gameState == GameState.WAITING) {
            val minPlayers = gameManager.getMinPlayers()
            val totalPlayers = hunters.size + runners.size
            addScoreboardLine("§e必要人数: §f${totalPlayers}/${minPlayers}", line--)
            addScoreboardLine("§r    ", line--) // 空行
        }
        
        // コマンド情報
        addScoreboardLine("§7━━━━━━━━━━━━━", line--)
        addScoreboardLine("§f/manhunt help", line--)
        addScoreboardLine("§7でコマンド確認", line--)
        
        // 各プレイヤーに個別のスコアボードを作成・適用
        onlinePlayers.forEach { player ->
            createPlayerScoreboard(player)
        }
    }
    
    private fun createPlayerScoreboard(player: Player) {
        // 各プレイヤー用の完全なスコアボードを作成
        val playerScoreboard = Bukkit.getScoreboardManager()?.newScoreboard ?: return
        val playerObjective = playerScoreboard.registerNewObjective("manhunt", "dummy", "§6🏃 MANHUNT")
        playerObjective.displaySlot = DisplaySlot.SIDEBAR
        
        val gameState = gameManager.getGameState()
        val hunters = gameManager.getAllHunters().filter { it.isOnline }
        val runners = gameManager.getAllRunners().filter { it.isOnline }
        val spectators = gameManager.getAllSpectators().filter { it.isOnline }
        val role = gameManager.getPlayerRole(player)
        
        var line = 15
        
        // ゲーム状態表示
        addPlayerScoreboardLine(playerObjective, "§r", line--) // 空行
        addPlayerScoreboardLine(playerObjective, "§f状態: ${getGameStateDisplay(gameState)}", line--)
        addPlayerScoreboardLine(playerObjective, "§r ", line--) // 空行
        
        // ゲーム状態に応じた詳細情報
        if (gameState == GameState.RUNNING) {
            // ゲーム中：生存数・死亡数を表示
            val aliveHunters = hunters.filter { !it.isDead }
            val aliveRunners = runners.filter { !it.isDead }
            val deadHunters = hunters.filter { it.isDead }
            val deadRunners = runners.filter { it.isDead }
            
            addPlayerScoreboardLine(playerObjective, "§c🗡 ハンター生存: §f${aliveHunters.size}", line--)
            addPlayerScoreboardLine(playerObjective, "§c💀 ハンター死亡: §f${deadHunters.size}", line--)
            addPlayerScoreboardLine(playerObjective, "§a🏃 ランナー生存: §f${aliveRunners.size}", line--)
            addPlayerScoreboardLine(playerObjective, "§a💀 ランナー死亡: §f${deadRunners.size}", line--)
            addPlayerScoreboardLine(playerObjective, "§r   ", line--) // 空行
        } else {
            // ゲーム開始前：プレイヤー数表示
            addPlayerScoreboardLine(playerObjective, "§c🗡 ハンター: §f${hunters.size}", line--)
            addPlayerScoreboardLine(playerObjective, "§a🏃 ランナー: §f${runners.size}", line--)
            addPlayerScoreboardLine(playerObjective, "§7👁 観戦者: §f${spectators.size}", line--)
            addPlayerScoreboardLine(playerObjective, "§r  ", line--) // 空行
        }
        
        
        // 待機中の場合は追加情報
        if (gameState == GameState.WAITING) {
            val minPlayers = gameManager.getMinPlayers()
            val totalPlayers = hunters.size + runners.size
            addPlayerScoreboardLine(playerObjective, "§e必要人数: §f${totalPlayers}/${minPlayers}", line--)
            addPlayerScoreboardLine(playerObjective, "§r    ", line--) // 空行
        }
        
        // コマンド情報
        addPlayerScoreboardLine(playerObjective, "§7━━━━━━━━━━━━━", line--)
        addPlayerScoreboardLine(playerObjective, "§f/manhunt help", line--)
        addPlayerScoreboardLine(playerObjective, "§7でコマンド確認", line--)
        
        // プレイヤーリスト（Tabキー）表示を設定
        setupPlayerListDisplay(player, playerScoreboard)
        
        // プレイヤーにスコアボードを適用
        player.scoreboard = playerScoreboard
    }
    
    private fun setupPlayerListDisplay(viewer: Player, scoreboard: Scoreboard) {
        // プレイヤーリスト用のObjectiveを作成
        val playerListObjective = scoreboard.registerNewObjective("playerlist", "dummy", "§6プレイヤー情報")
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
                    selfTeam.suffix = " §f(自分)"
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
            "座標取得エラー"
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
                        "§e/manhunt join でゲームに参加しよう！"
                    } else {
                        "§7役割: ${getRoleDisplay(role)} §8| §eゲーム開始を待機中..."
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
                                "§c🗡 ハンターモード §8| §f最寄りターゲット: §a${nearestRunner.name} §7(${distance}m)"
                            } else {
                                "§c🗡 ハンターモード §8| §7ターゲットが見つかりません"
                            }
                        }
                        PlayerRole.RUNNER -> {
                            "§a🏃 ランナーモード §8| §7エンダードラゴンを倒そう！"
                        }
                        PlayerRole.SPECTATOR -> "§7👁 観戦モード §8| §eゲームを観戦中..."
                    }
                }
                else -> "§7ゲーム情報: /manhunt status"
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
        val (title, subtitle, color) = when (newState) {
            GameState.STARTING -> Triple("§6ゲーム開始", "§e準備してください...", BarColor.YELLOW)
            GameState.RUNNING -> Triple("§a🏃 MANHUNT", "§fゲーム開始！", BarColor.GREEN)
            GameState.ENDED -> Triple("§cゲーム終了", "§7お疲れ様でした", BarColor.RED)
            else -> return
        }
        
        Bukkit.getOnlinePlayers().forEach { player ->
            showTitle(player, title, subtitle)
            if (newState == GameState.RUNNING) {
                showGameProgressBossBar(player, "§6🏃 Manhunt Game 進行中", 1.0, color)
            }
        }
    }
    
    // ======== 便利メソッド ========
    
    private fun getGameStateDisplay(state: GameState): String = when (state) {
        GameState.WAITING -> "§e待機中"
        GameState.STARTING -> "§6開始中"
        GameState.RUNNING -> "§a進行中"
        GameState.ENDED -> "§c終了"
    }
    
    private fun getRoleDisplay(role: PlayerRole): String = when (role) {
        PlayerRole.HUNTER -> "§c🗡 ハンター"
        PlayerRole.RUNNER -> "§a🏃 ランナー"
        PlayerRole.SPECTATOR -> "§7👁 観戦者"
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
            showGameProgressBossBar(player, "§6🏃 Manhunt Game 進行中", 1.0, BarColor.GREEN)
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