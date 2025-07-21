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
    private val resetCountdownBossBars = mutableMapOf<Player, BossBar>()
    
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
    
    fun updateScoreboardForAllPlayers() {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) return
        
        clearScoreboardEntries()
        
        val gameState = gameManager.getGameState()
        val hunters = gameManager.getAllHunters().filter { it.isOnline }
        val runners = gameManager.getAllRunners().filter { it.isOnline }
        val spectators = gameManager.getAllSpectators().filter { it.isOnline }
        
        var line = 15
        
        // 空行のみ
        addScoreboardLine("§r", line--) // 空行
        
        // ゲーム状態に応じた詳細情報
        if (gameState == GameState.RUNNING) {
            // ゲーム中：生存数・死亡数をアイコンのみで表示
            val aliveHunters = hunters.filter { !it.isDead }
            val aliveRunners = runners.filter { !gameManager.isRunnerDead(it) }
            val deadHunters = hunters.filter { it.isDead }
            val deadRunners = runners.filter { gameManager.isRunnerDead(it) }
            
            addScoreboardLine("§c🗡 §f${aliveHunters.size}  §c💀 §f${deadHunters.size}", line--)
            addScoreboardLine("§a🏃 §f${aliveRunners.size}  §a💀 §f${deadRunners.size}", line--)
            
            // リスポン待ち中のランナーがいる場合、追加表示
            if (deadRunners.isNotEmpty()) {
                val respawningCount = gameManager.getDeadRunners().size
                if (respawningCount > 0) {
                    addScoreboardLine(messageManager.getMessage("ui.scoreboard.runners-respawning", "count" to respawningCount), line--)
                }
            }
            
            addScoreboardLine("§r   ", line--) // 空行
        } else {
            // ゲーム開始前：プレイヤー数表示
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.hunters-total", "count" to hunters.size), line--)
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.runners-total", "count" to runners.size), line--)
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.spectators-total", "count" to spectators.size), line--)
            addScoreboardLine("§r  ", line--) // 空行
        }
        
        // 待機中の場合のみ必要人数を表示
        if (gameState == GameState.WAITING) {
            val minPlayers = gameManager.getMinPlayers()
            val totalPlayers = hunters.size + runners.size
            addScoreboardLine(messageManager.getMessage("ui.scoreboard.required-players", "current" to totalPlayers, "min" to minPlayers), line--)
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
        
        // チームを作成（各プレイヤーのスコアボードに）
        createTeamsForScoreboard(playerScoreboard)
        assignPlayersToScoreboardTeams(playerScoreboard)
        
        val gameState = gameManager.getGameState()
        val hunters = gameManager.getAllHunters().filter { it.isOnline }
        val runners = gameManager.getAllRunners().filter { it.isOnline }
        val spectators = gameManager.getAllSpectators().filter { it.isOnline }
        val role = gameManager.getPlayerRole(player)
        
        var line = 15
        
        // 空行のみ
        addPlayerScoreboardLine(playerObjective, "§r", line--) // 空行
        
        // 自分のロールを表示
        if (role != null) {
            val roleDisplay = when (role) {
                PlayerRole.HUNTER -> "§c🗡 " + messageManager.getMessage(player, "role.hunter")
                PlayerRole.RUNNER -> "§a🏃 " + messageManager.getMessage(player, "role.runner") 
                PlayerRole.SPECTATOR -> "§7👁 " + messageManager.getMessage(player, "role.spectator")
            }
            addPlayerScoreboardLine(playerObjective, roleDisplay, line--)
            addPlayerScoreboardLine(playerObjective, "§r ", line--) // 空行
        }
        
        // ゲーム状態に応じた詳細情報
        if (gameState == GameState.RUNNING) {
            // ゲーム中：生存数・死亡数をアイコンのみで表示
            val aliveHunters = hunters.filter { !it.isDead }
            val aliveRunners = runners.filter { !gameManager.isRunnerDead(it) }
            val deadHunters = hunters.filter { it.isDead }
            val deadRunners = runners.filter { gameManager.isRunnerDead(it) }
            
            addPlayerScoreboardLine(playerObjective, "§c🗡 §f${aliveHunters.size}  §c💀 §f${deadHunters.size}", line--)
            
            // ランナーの表示行
            addPlayerScoreboardLine(playerObjective, "§a🏃 §f${aliveRunners.size}  §a💀 §f${deadRunners.size}", line--)
            
            // リスポン待ち中のランナーがいる場合、追加表示
            if (deadRunners.isNotEmpty()) {
                val respawningCount = gameManager.getDeadRunners().size
                if (respawningCount > 0) {
                    addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.runners-respawning", "count" to respawningCount), line--)
                }
            }
            
            // プレイヤーが死亡中のランナーの場合、自分の復活時間を表示
            if (role == PlayerRole.RUNNER && gameManager.isRunnerDead(player)) {
                val respawnTime = gameManager.getRespawnTimeForPlayer(player)
                if (respawnTime > 0) {
                    addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.respawn-time", "time" to respawnTime), line--)
                }
            }
            
            addPlayerScoreboardLine(playerObjective, "§r   ", line--) // 空行
            
            // タイムモードの場合は残り時間と優勢度を表示
            if (configManager.isTimeLimitMode()) {
                // 残り時間
                val remainingTime = gameManager.getRemainingTime()
                val remainingMinutes = remainingTime / 60
                val remainingSeconds = remainingTime % 60
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.remaining-time", "minutes" to remainingMinutes, "seconds" to String.format("%02d", remainingSeconds)), line--)
                
                // 優勢度
                val dominancePercent = gameManager.getHunterDominancePercentage()
                val runnerPercent = 100 - dominancePercent
                val dominanceBar = createDominanceBar(dominancePercent)
                addPlayerScoreboardLine(playerObjective, "§c" + dominancePercent + "% " + dominanceBar + " §a" + runnerPercent + "%", line--)
            } else {
                // 通常モードは経過時間を表示
                val elapsedTime = gameManager.getGameElapsedTime()
                val minutes = elapsedTime / 60
                val seconds = elapsedTime % 60
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.elapsed-time", "minutes" to minutes, "seconds" to String.format("%02d", seconds)), line--)
            }
            
            // 所持金表示（ゲーム中のみ）
            if (role != null && role != PlayerRole.SPECTATOR) {
                val balance = plugin.getEconomyManager().getBalance(player)
                val unit = plugin.getConfigManager().getCurrencyConfig().currencyUnit
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.balance", "balance" to balance, "unit" to unit), line--)
                addPlayerScoreboardLine(playerObjective, "§r     ", line--) // 空行
            }
        } else {
            // ゲーム開始前：プレイヤー数表示
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.hunters-total", "count" to hunters.size), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.runners-total", "count" to runners.size), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.spectators-total", "count" to spectators.size), line--)
            addPlayerScoreboardLine(playerObjective, "§r  ", line--) // 空行
        }
        
        
        // 待機中の場合のみ必要人数を表示
        if (gameState == GameState.WAITING) {
            val minPlayers = gameManager.getMinPlayers()
            val totalPlayers = hunters.size + runners.size
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.required-players", "current" to totalPlayers, "min" to minPlayers), line--)
            addPlayerScoreboardLine(playerObjective, "§r    ", line--) // 空行
        }
        
        // バディー情報表示
        if (gameState == GameState.RUNNING && role != null && role != PlayerRole.SPECTATOR) {
            val buddyInfo = plugin.getBuddySystem().getBuddyRelativeCoordinates(player)
            val buddy = plugin.getBuddySystem().getBuddy(player)
            
            if (buddy != null && buddyInfo != null) {
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.separator"), line--)
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.buddy-title", "buddy" to buddy.name), line--)
                addPlayerScoreboardLine(playerObjective, buddyInfo, line--)
            } else {
                // バディーがいない場合はコマンド情報を表示
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.separator"), line--)
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.help-command"), line--)
                addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.help-text"), line--)
            }
        } else {
            // ゲーム開始前またはスペクテーターの場合はコマンド情報を表示
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.separator"), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.help-command"), line--)
            addPlayerScoreboardLine(playerObjective, messageManager.getMessage(player, "ui.scoreboard.help-text"), line--)
        }
        
        // プレイヤーリスト（Tabキー）表示を設定
        setupPlayerListDisplay(player, playerScoreboard)
        
        // プレイヤーにスコアボードを適用
        player.scoreboard = playerScoreboard
    }
    
    private fun createTeamsForScoreboard(scoreboard: Scoreboard) {
        // 既存のチームを削除
        scoreboard.getTeam("manhunt_hunters")?.unregister()
        scoreboard.getTeam("manhunt_runners")?.unregister()
        scoreboard.getTeam("manhunt_hidden")?.unregister()
        
        // 設定に基づいてチームを作成
        val visibilityMode = plugin.getConfigManager().getNameTagVisibilityMode()
        val gameState = gameManager.getGameState()
        val shouldHide = plugin.getConfigManager().isHideNameTagsDuringGame() && gameState == GameState.RUNNING
        
        when {
            shouldHide && visibilityMode == "all" -> {
                // 全員の名前を隠すチームを作成
                val hiddenTeam = scoreboard.registerNewTeam("manhunt_hidden").apply {
                    setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
                    setCanSeeFriendlyInvisibles(false)
                }
            }
            shouldHide && visibilityMode == "team" -> {
                // チーム内のみ表示
                val hunterTeam = scoreboard.registerNewTeam("manhunt_hunters").apply {
                    setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM)
                    setCanSeeFriendlyInvisibles(true)
                    color = org.bukkit.ChatColor.RED
                }
                
                val runnerTeam = scoreboard.registerNewTeam("manhunt_runners").apply {
                    setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM)
                    setCanSeeFriendlyInvisibles(true)
                    color = org.bukkit.ChatColor.GREEN
                }
            }
            else -> {
                // 通常表示（ゲーム終了時や設定がoffの場合）
                // チームを作成しても名前タグは常に表示
                val hunterTeam = scoreboard.registerNewTeam("manhunt_hunters").apply {
                    setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
                    color = org.bukkit.ChatColor.RED
                }
                
                val runnerTeam = scoreboard.registerNewTeam("manhunt_runners").apply {
                    setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
                    color = org.bukkit.ChatColor.GREEN
                }
            }
        }
    }
    
    private fun assignPlayersToScoreboardTeams(scoreboard: Scoreboard) {
        val visibilityMode = plugin.getConfigManager().getNameTagVisibilityMode()
        val gameState = gameManager.getGameState()
        val shouldHide = plugin.getConfigManager().isHideNameTagsDuringGame() && gameState == GameState.RUNNING
        
        if (shouldHide && visibilityMode == "all") {
            // 全員をhiddenチームに追加
            val hiddenTeam = scoreboard.getTeam("manhunt_hidden")
            Bukkit.getOnlinePlayers().forEach { player ->
                hiddenTeam?.addEntry(player.name)
            }
        } else {
            // 通常のチーム分け
            val hunterTeam = scoreboard.getTeam("manhunt_hunters")
            val runnerTeam = scoreboard.getTeam("manhunt_runners")
            
            // ハンターをチームに追加
            gameManager.getAllHunters().filter { it.isOnline }.forEach { player ->
                hunterTeam?.addEntry(player.name)
            }
            
            // ランナーをチームに追加
            gameManager.getAllRunners().filter { it.isOnline }.forEach { player ->
                runnerTeam?.addEntry(player.name)
            }
        }
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
                    // 自分自身は役割に応じた色で表示
                    val selfTeam = scoreboard.getTeam("self_${target.name}") ?: scoreboard.registerNewTeam("self_${target.name}")
                    selfTeam.color = when (viewerRole) {
                        PlayerRole.HUNTER -> org.bukkit.ChatColor.RED
                        PlayerRole.RUNNER -> org.bukkit.ChatColor.BLUE
                        else -> org.bukkit.ChatColor.GRAY
                    }
                    selfTeam.prefix = ""
                    selfTeam.suffix = ""
                    selfTeam.addEntry(target.name)
                } else if (isAlly(viewerRole, targetRole)) {
                    // 味方同士のみ表示（同じ役割かつ観戦者以外）
                    
                    // バディーかどうかチェック
                    val buddy = plugin.getBuddySystem().getBuddy(viewer)
                    val isBuddy = buddy == target
                    
                    // チーム設定で名前の色を変更
                    val teamName = if (isBuddy) "buddy_${target.name}" else "ally_${target.name}"
                    var team = scoreboard.getTeam(teamName)
                    if (team == null) {
                        team = scoreboard.registerNewTeam(teamName)
                        team.color = if (isBuddy) {
                            org.bukkit.ChatColor.GOLD  // バディーはオレンジ（金色）
                        } else {
                            when (targetRole) {
                                PlayerRole.HUNTER -> org.bukkit.ChatColor.RED
                                PlayerRole.RUNNER -> org.bukkit.ChatColor.BLUE
                                else -> org.bukkit.ChatColor.GRAY
                            }
                        }
                    }
                    team.prefix = ""
                    team.suffix = ""
                    team.addEntry(target.name)
                }
            }
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
        // Minecraftのスコアボードは1行40文字まで対応（1.13以降）
        objective.getScore(text).score = score
    }
    
    private fun addScoreboardLine(text: String, score: Int) {
        // Minecraftのスコアボードは1行40文字まで対応（1.13以降）
        objective?.getScore(text)?.score = score
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
            
            // ゲーム中のプレイヤー状態を表示
            val statusMessage = when {
                gameState == GameState.WAITING -> {
                    if (role == null) {
                        messageManager.getMessage(player, "ui.actionbar.join-game")
                    } else {
                        val roleDisplay = when (role) {
                            PlayerRole.HUNTER -> messageManager.getMessage(player, "ui.actionbar.role.hunter")
                            PlayerRole.RUNNER -> messageManager.getMessage(player, "ui.actionbar.role.runner")
                            PlayerRole.SPECTATOR -> messageManager.getMessage(player, "ui.actionbar.role.spectator")
                        }
                        messageManager.getMessage(player, "ui.actionbar.waiting", "role" to roleDisplay)
                    }
                }
                gameState == GameState.STARTING -> {
                    messageManager.getMessage(player, "ui.actionbar.starting")
                }
                gameState == GameState.RUNNING && role != null -> {
                    // プレイヤーの現在の状態を表示
                    val stateDisplay = if (player.isDead && role == PlayerRole.RUNNER) {
                        val respawnTime = gameManager.getRespawnTimeForPlayer(player)
                        if (respawnTime > 0) {
                            messageManager.getMessage(player, "ui.actionbar.respawning", "time" to respawnTime)
                        } else {
                            messageManager.getMessage(player, "ui.actionbar.dead")
                        }
                    } else {
                        when (role) {
                            PlayerRole.HUNTER -> messageManager.getMessage(player, "ui.actionbar.role.hunter")
                            PlayerRole.RUNNER -> messageManager.getMessage(player, "ui.actionbar.role.runner") 
                            PlayerRole.SPECTATOR -> messageManager.getMessage(player, "ui.actionbar.role.spectator")
                        }
                    }
                    
                    val targetInfo = when (role) {
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
                                " | " + messageManager.getMessage(player, "ui.actionbar.hunter-with-target", "target" to nearestRunner.name, "distance" to distance)
                            } else {
                                " | " + messageManager.getMessage(player, "ui.actionbar.hunter-no-target")
                            }
                        }
                        PlayerRole.RUNNER -> {
                            // 近接警告をチェック
                            val proximityWarning = if (!gameManager.isRunnerDead(player)) {
                                gameManager.getProximityWarningForRunner(player)
                            } else {
                                null
                            }
                            
                            if (proximityWarning != null) {
                                " | $proximityWarning"
                            } else {
                                " | " + messageManager.getMessage(player, "ui.actionbar.runner-objective")
                            }
                        }
                        PlayerRole.SPECTATOR -> ""
                    }
                    
                    stateDisplay + targetInfo
                }
                else -> messageManager.getMessage(player, "ui.actionbar.default")
            }
            
            sendActionBar(player, statusMessage)
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
    
    fun showRespawnBossBar(player: Player, remainingTime: Int, totalTime: Int) {
        if (!configManager.isBossBarEnabled()) return
        
        removeBossBar(player)
        
        val title = messageManager.getMessage(player, "ui.bossbar.respawn-title", "time" to remainingTime)
        val progress = remainingTime.toDouble() / totalTime.toDouble()
        
        val bossBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID)
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
        resetCountdownBossBars.values.forEach { it.removeAll() }
        resetCountdownBossBars.clear()
    }
    
    // ======== リセットカウントダウンBossBar ========
    
    fun showResetCountdownBossBar(player: Player, title: String, progress: Double) {
        if (!configManager.isBossBarEnabled()) return
        
        // 既存のBossBarがあれば更新、なければ新規作成
        val bossBar = resetCountdownBossBars.getOrPut(player) {
            val newBossBar = Bukkit.createBossBar(title, BarColor.BLUE, BarStyle.SOLID)
            newBossBar.addPlayer(player)
            newBossBar.isVisible = true
            newBossBar
        }
        
        bossBar.setTitle(title)
        bossBar.progress = progress.coerceIn(0.0, 1.0)
        
        // 残り時間によって色を変更
        when {
            progress > 0.5 -> bossBar.color = BarColor.BLUE
            progress > 0.2 -> bossBar.color = BarColor.YELLOW
            else -> bossBar.color = BarColor.RED
        }
    }
    
    fun removeResetCountdownBossBar(player: Player) {
        resetCountdownBossBars[player]?.let { bossBar ->
            bossBar.removeAll()
            resetCountdownBossBars.remove(player)
        }
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
            // ボスバーは復活時間表示専用になったため、ゲーム開始時は表示しない
        }
        
        // ゲーム終了時は全てのBossBarをクリア
        if (newState == GameState.ENDED) {
            clearAllBossBars()
        }
        
        // ゲーム状態が変わったらスコアボードを即座に更新（名前タグの表示設定も反映される）
        updateScoreboardImmediately()
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
        
        // ボスバーは復活時間表示専用になったため、通常時は表示しない
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
    
    /**
     * 優勢度を視覚的なバーで表現
     */
    private fun createDominanceBar(hunterPercent: Int): String {
        val totalLength = 10
        val hunterBars = (hunterPercent * totalLength) / 100
        val runnerBars = totalLength - hunterBars
        
        val bar = StringBuilder()
        // ハンター側（赤）
        bar.append("§c")
        for (i in 0 until hunterBars) {
            bar.append("█")
        }
        // ランナー側（緑）
        bar.append("§a")
        for (i in 0 until runnerBars) {
            bar.append("█")
        }
        
        return bar.toString()
    }
}
