package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.Team
import java.util.*
import kotlin.random.Random
import kotlin.math.cos
import kotlin.math.sin

class GameManager(private val plugin: Main, val configManager: ConfigManager, private val messageManager: MessageManager) {
    private var gameState = GameState.WAITING
    private val players = mutableMapOf<UUID, ManhuntPlayer>()
    private val fixedHunters = mutableSetOf<UUID>()
    private var minPlayers = configManager.getMinPlayers()
    private var proximityTask: BukkitRunnable? = null
    
    // 統計とリザルト管理
    private val gameStats = GameStats()
    private lateinit var gameResultManager: GameResultManager
    
    fun getPlugin(): Main = plugin
    
    // 統計とリザルト管理の初期化
    fun initialize() {
        gameResultManager = GameResultManager(plugin, this, messageManager)
    }
    
    /**
     * ダメージ統計を記録
     */
    fun recordDamage(attacker: Player, victim: Player, damage: Double) {
        if (gameState == GameState.RUNNING) {
            gameStats.addDamage(attacker, victim, damage)
        }
    }
    
    /**
     * キル統計を記録
     */
    fun recordKill(killer: Player, victim: Player) {
        if (gameState == GameState.RUNNING) {
            gameStats.addKill(killer, victim)
        }
    }
    
    /**
     * 通貨獲得統計を記録
     */
    fun recordEarnedCurrency(player: Player, amount: Int) {
        if (gameState == GameState.RUNNING) {
            gameStats.addEarnedCurrency(player, amount)
        }
    }
    
    /**
     * 通貨消費統計を記録
     */
    fun recordSpentCurrency(player: Player, amount: Int) {
        if (gameState == GameState.RUNNING) {
            gameStats.addSpentCurrency(player, amount)
        }
    }
    
    /**
     * ディメンション訪問統計を記録
     */
    fun recordDimensionVisit(player: Player, worldName: String) {
        if (gameState == GameState.RUNNING) {
            gameStats.addDimensionVisit(player, worldName)
        }
    }
    
    /**
     * ダイヤモンド収集統計を記録
     */
    fun recordDiamondCollected(player: Player, count: Int = 1) {
        if (gameState == GameState.RUNNING) {
            gameStats.addDiamondCollected(player, count)
        }
    }
    
    /**
     * リソースのクリーンアップ
     */
    fun cleanup() {
        try {
            if (::gameResultManager.isInitialized) {
                gameResultManager.cleanup()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error during result manager cleanup: ${e.message}")
        }
    }
    
    // ネットワークエラーで退出したプレイヤーの情報を保持
    private val disconnectedPlayers = mutableMapOf<UUID, PlayerRole>()
    
    // ゲーム開始前のゲームモードを保存
    private val originalGameModes = mutableMapOf<UUID, GameMode>()
    
    // パフォーマンス最適化用キャッシュ
    private var cachedHunters: List<Player>? = null
    private var cachedRunners: List<Player>? = null
    private var hunterCacheExpiry = 0L
    private var runnerCacheExpiry = 0L
    private val CACHE_DURATION = 500L // 0.5秒キャッシュ
    
    // リスポン管理
    private val deadRunners = mutableMapOf<UUID, Long>() // プレイヤーID -> 死亡時刻
    private val respawnTasks = mutableMapOf<UUID, BukkitRunnable>() // プレイヤーID -> リスポンタスク
    private val countdownTasks = mutableMapOf<UUID, BukkitRunnable>() // プレイヤーID -> カウントダウンタスク
    
    // 近接警告のクールダウン管理
    private val proximityWarningCooldowns = mutableMapOf<UUID, Long>() // プレイヤーID -> 最後の警告時刻
    
    // ゲーム開始カウントダウン管理
    private var countdownTask: BukkitRunnable? = null
    
    // チーム管理
    private var hunterTeam: Team? = null
    private var runnerTeam: Team? = null
    
    fun getGameState(): GameState = gameState
    
    fun addPlayer(player: Player, role: PlayerRole) {
        players[player.uniqueId] = ManhuntPlayer(player, role)
        invalidateCache()
        checkStartConditions()
        
        // 統計情報にプレイヤーを追加
        if (gameState == GameState.RUNNING) {
            gameStats.addPlayer(player, role)
        }
        
        // UIの即座更新
        try {
            plugin.getUIManager().updateScoreboardImmediately()
        } catch (e: Exception) {
            plugin.logger.warning("UI即座更新でエラー: ${e.message}")
        }
    }
    
    fun removePlayer(player: Player, isIntentionalLeave: Boolean = false) {
        val wasInGame = players.containsKey(player.uniqueId)
        val playerRole = players[player.uniqueId]?.role
        
        // チームから削除
        if (playerRole != null) {
            when (playerRole) {
                PlayerRole.HUNTER -> hunterTeam?.removeEntry(player.name)
                PlayerRole.RUNNER -> runnerTeam?.removeEntry(player.name)
                PlayerRole.SPECTATOR -> {} // 観戦者はチームに入っていない
            }
        }
        
        if (gameState == GameState.RUNNING && wasInGame) {
            // ゲーム進行中にプレイヤーが退出した場合
            
            // 統計情報に退出を記録
            try {
                gameStats.playerLeft(player)
            } catch (e: Exception) {
                plugin.logger.warning("Error recording player exit statistics: ${e.message}")
            }
            
            if (isIntentionalLeave) {
                // 意図的な退出の場合はSpectatorにする
                setPlayerRole(player, PlayerRole.SPECTATOR)
                player.sendMessage(messageManager.getMessage(player, "quit.changed-to-spectator"))
                Bukkit.broadcastMessage(messageManager.getMessage("game-management.player-left-spectator", mapOf("player" to player.name)))
            } else {
                // 切断の場合は元の役割を保存（ネットワークエラーの可能性）
                playerRole?.let { role ->
                    disconnectedPlayers[player.uniqueId] = role
                    players.remove(player.uniqueId)
                    fixedHunters.remove(player.uniqueId)
                    Bukkit.broadcastMessage(messageManager.getMessage("game-management.player-disconnected", mapOf("player" to player.name)))
                }
            }
            
            // 勝利条件をチェック
            checkWinConditionsAfterLeave(playerRole)
        } else {
            // ゲーム待機中または参加していない場合
            players.remove(player.uniqueId)
            fixedHunters.remove(player.uniqueId)
        }
        
        invalidateCache()
        
        // UIの即座更新
        try {
            plugin.getUIManager().updateScoreboardImmediately()
        } catch (e: Exception) {
            plugin.logger.warning("UI即座更新でエラー: ${e.message}")
        }
    }
    
    // ネットワークエラーで退出したプレイヤーの再参加処理
    fun handleRejoin(player: Player): Boolean {
        val disconnectedRole = disconnectedPlayers[player.uniqueId]
        
        if (disconnectedRole != null && gameState == GameState.RUNNING) {
            // 元の役割で再参加
            players[player.uniqueId] = ManhuntPlayer(player, disconnectedRole)
            if (disconnectedRole == PlayerRole.HUNTER) {
                fixedHunters.add(player.uniqueId)
            }
            disconnectedPlayers.remove(player.uniqueId)
            
            // ゲームモードをSpectatorに設定
            player.gameMode = GameMode.SPECTATOR
            
            val roleText = when (disconnectedRole) {
                PlayerRole.RUNNER -> messageManager.getMessage(player, "role-display.runner")
                PlayerRole.HUNTER -> messageManager.getMessage(player, "role-display.hunter")
                PlayerRole.SPECTATOR -> messageManager.getMessage(player, "role-display.spectator")
            }
            
            player.sendMessage(messageManager.getMessage(player, "game-management.network-recovery", mapOf("role" to roleText)))
            Bukkit.broadcastMessage(messageManager.getMessage("game-management.network-recovery-broadcast", mapOf("player" to player.name)))
            return true
        }
        
        return false
    }
    
    fun setPlayerRole(player: Player, role: PlayerRole) {
        val oldRole = players[player.uniqueId]?.role
        plugin.logger.info("Role change: ${player.name} ${oldRole} -> ${role}")
        
        // プレイヤーがゲームに参加していない場合は自動的に参加させる
        if (!players.containsKey(player.uniqueId)) {
            players[player.uniqueId] = ManhuntPlayer(player, role)
            plugin.logger.info("New player added: ${player.name} as ${role}")
        } else {
            players[player.uniqueId]?.role = role
            plugin.logger.info("Existing player role changed: ${player.name} to ${role}")
            
            // 統計情報の役割を更新
            if (gameState == GameState.RUNNING) {
                gameStats.updatePlayerRole(player, role)
            }
        }
        
        // チーム割り当てを更新（ゲーム中の場合）
        if (gameState == GameState.RUNNING) {
            // 既存のチームから削除
            hunterTeam?.removeEntry(player.name)
            runnerTeam?.removeEntry(player.name)
            
            // 新しいチームに追加
            when (role) {
                PlayerRole.HUNTER -> hunterTeam?.addEntry(player.name)
                PlayerRole.RUNNER -> runnerTeam?.addEntry(player.name)
                PlayerRole.SPECTATOR -> {} // 観戦者はチームに入らない
            }
        }
        
        if (role == PlayerRole.HUNTER) {
            fixedHunters.add(player.uniqueId)
        } else {
            fixedHunters.remove(player.uniqueId)
        }
        invalidateCache()
        
        // 変更後の状態をログ出力
        val hunters = getAllHunters()
        val runners = getAllRunners()
        val spectators = getAllSpectators()
        plugin.logger.info("Post-role-change status: Hunters ${hunters.size}, Runners ${runners.size}, Spectators ${spectators.size}")
        plugin.logger.info("Hunters: ${hunters.map { it.name }}")
        plugin.logger.info("Runners: ${runners.map { it.name }}")
        plugin.logger.info("Spectators: ${spectators.map { it.name }}")
        
        // UIの即座更新
        try {
            plugin.getUIManager().updateScoreboardImmediately()
        } catch (e: Exception) {
            plugin.logger.warning("UI即座更新でエラー: ${e.message}")
        }
    }
    
    fun getPlayerRole(player: Player): PlayerRole? {
        return players[player.uniqueId]?.role
    }
    
    fun getAllRunners(): List<Player> {
        val currentTime = System.currentTimeMillis()
        if (cachedRunners == null || currentTime > runnerCacheExpiry) {
            val allPlayers = players.values.map { "${it.player.name}(${it.role})" }
            //plugin.logger.info("ランナーキャッシュ更新中: 全プレイヤー=${allPlayers}")
            cachedRunners = players.values.filter { it.role == PlayerRole.RUNNER }.map { it.player }
            runnerCacheExpiry = currentTime + CACHE_DURATION
            //plugin.logger.info("ランナーキャッシュ更新完了: ${cachedRunners!!.map { it.name }}")
        }
        return cachedRunners!!
    }
    
    fun getAllHunters(): List<Player> {
        val currentTime = System.currentTimeMillis()
        if (cachedHunters == null || currentTime > hunterCacheExpiry) {
            val allPlayers = players.values.map { "${it.player.name}(${it.role})" }
            //plugin.logger.info("ハンターキャッシュ更新中: 全プレイヤー=${allPlayers}")
            cachedHunters = players.values.filter { it.role == PlayerRole.HUNTER }.map { it.player }
            hunterCacheExpiry = currentTime + CACHE_DURATION
            //plugin.logger.info("ハンターキャッシュ更新完了: ${cachedHunters!!.map { it.name }}")
        }
        return cachedHunters!!
    }
    
    fun getAllSpectators(): List<Player> {
        return players.values.filter { it.role == PlayerRole.SPECTATOR }.map { it.player }
    }
    
    private fun invalidateCache() {
        cachedHunters = null
        cachedRunners = null
        hunterCacheExpiry = 0L
        runnerCacheExpiry = 0L
    }
    
    private fun checkStartConditions() {
        plugin.logger.info("Start condition check: Game state=${gameState}, Player count=${players.size}, Min players=${minPlayers}")
        
        if (gameState == GameState.WAITING) {
            val hunters = getAllHunters()
            val runners = getAllRunners()
            val activePlayerCount = hunters.size + runners.size // 観戦者を除外
            
            plugin.logger.info("Detailed check: Hunters=${hunters.size}, Runners=${runners.size}, Active players=${activePlayerCount}")
            plugin.logger.info("Hunters: ${hunters.map { it.name }}")
            plugin.logger.info("Runners: ${runners.map { it.name }}")
            
            if (activePlayerCount >= minPlayers && hunters.isNotEmpty() && runners.isNotEmpty()) {
                plugin.logger.info("Start conditions met! Starting game.")
                startGame()
            } else {
                if (activePlayerCount < minPlayers) {
                    plugin.logger.info("Start conditions not met: Insufficient active players (${activePlayerCount}/${minPlayers})")
                } else {
                    plugin.logger.info("Start conditions not met: Insufficient hunters(${hunters.size}) or runners(${runners.size})")
                }
            }
        } else {
            plugin.logger.info("Start conditions not met: Game state=${gameState} is not WAITING")
        }
    }
    
    fun forceStartGame() {
        if (gameState == GameState.WAITING) {
            startGame()
        }
    }
    
    private fun startGame() {
        gameState = GameState.STARTING
        
        // UIにゲーム開始を通知
        try {
            plugin.getUIManager().showGameStateChange(GameState.STARTING)
        } catch (e: Exception) {
            plugin.logger.warning("UI通知でエラー: ${e.message}")
        }
        
        // 全プレイヤーのゲームモードを保存
        for (player in Bukkit.getOnlinePlayers()) {
            originalGameModes[player.uniqueId] = player.gameMode
        }
        
        // 役割が未割り当ての場合のみ自動割り当て
        assignRolesIfNeeded()
        
        // カウントダウン開始
        startGameCountdown()
    }
    
    private fun assignRolesIfNeeded() {
        val unassigned = players.values.filter { it.role == PlayerRole.SPECTATOR }
        val hunters = getAllHunters()
        val runners = getAllRunners()
        
        // ハンターが0人の場合、1人を割り当て
        if (hunters.isEmpty() && unassigned.isNotEmpty()) {
            unassigned.first().role = PlayerRole.HUNTER
            unassigned.first().player.sendMessage(messageManager.getMessage(unassigned.first().player, "game-management.hunter-shortage-assign"))
        }
        
        // ランナーが0人の場合、残りを割り当て
        if (runners.isEmpty() && unassigned.size > 1) {
            unassigned.drop(1).forEach { 
                it.role = PlayerRole.RUNNER
                it.player.sendMessage(messageManager.getMessage(it.player, "game-management.runner-shortage-assign"))
            }
        }
        
        // 最低人数チェック
        val finalHunters = getAllHunters()
        val finalRunners = getAllRunners()
        if (finalHunters.isEmpty() || finalRunners.isEmpty()) {
            gameState = GameState.WAITING
            Bukkit.broadcastMessage(messageManager.getMessage("game-management.start-cancelled-roles"))
            return
        }
    }
    
    private fun createTeams() {
        val scoreboardManager = Bukkit.getScoreboardManager() ?: return
        
        // 全プレイヤーに対してスコアボードを設定
        for (player in Bukkit.getOnlinePlayers()) {
            // プレイヤーが個別のスコアボードを持っている場合はメインスコアボードを設定
            if (player.scoreboard != scoreboardManager.mainScoreboard) {
                player.scoreboard = scoreboardManager.mainScoreboard
            }
        }
        
        val scoreboard = scoreboardManager.mainScoreboard
        
        // 既存のチームを削除
        scoreboard.getTeam("manhunt_hunters")?.unregister()
        scoreboard.getTeam("manhunt_runners")?.unregister()
        
        // ハンターチームを作成
        hunterTeam = scoreboard.registerNewTeam("manhunt_hunters").apply {
            setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM)
            setCanSeeFriendlyInvisibles(true)
            color = org.bukkit.ChatColor.RED
        }
        
        // ランナーチームを作成
        runnerTeam = scoreboard.registerNewTeam("manhunt_runners").apply {
            setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM)
            setCanSeeFriendlyInvisibles(true)
            color = org.bukkit.ChatColor.GREEN
        }
        
        plugin.logger.info("Teams created: hunters=${hunterTeam != null}, runners=${runnerTeam != null}")
    }
    
    private fun assignPlayersToTeams() {
        // 全プレイヤーをチームから削除
        hunterTeam?.entries?.toList()?.forEach { hunterTeam?.removeEntry(it) }
        runnerTeam?.entries?.toList()?.forEach { runnerTeam?.removeEntry(it) }
        
        var hunterCount = 0
        var runnerCount = 0
        
        // ハンターをチームに追加
        getAllHunters().filter { it.isOnline }.forEach { player ->
            hunterTeam?.addEntry(player.name)
            hunterCount++
            plugin.logger.info("Added ${player.name} to hunter team")
        }
        
        // ランナーをチームに追加
        getAllRunners().filter { it.isOnline }.forEach { player ->
            runnerTeam?.addEntry(player.name)
            runnerCount++
            plugin.logger.info("Added ${player.name} to runner team")
        }
        
        plugin.logger.info("Team assignment complete: hunters=$hunterCount, runners=$runnerCount")
        
        // デバッグ: チーム設定を確認
        hunterTeam?.let { team ->
            plugin.logger.info("Hunter team visibility: ${team.getOption(Team.Option.NAME_TAG_VISIBILITY)}")
        }
        runnerTeam?.let { team ->
            plugin.logger.info("Runner team visibility: ${team.getOption(Team.Option.NAME_TAG_VISIBILITY)}")
        }
    }
    
    private fun removeTeams() {
        hunterTeam?.unregister()
        runnerTeam?.unregister()
        hunterTeam = null
        runnerTeam = null
    }
    
    private fun startProximityChecking() {
        try {
            proximityTask?.cancel()
        } catch (e: Exception) {
            plugin.logger.warning("Error canceling existing proximity task: ${e.message}")
        }
        
        proximityTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    checkProximityWarnings()
                } catch (e: Exception) {
                    plugin.logger.warning("Error in proximity checking: ${e.message}")
                }
            }
        }
        proximityTask?.runTaskTimer(plugin, 0L, configManager.getProximityCheckInterval())
    }
    
    private fun checkProximityWarnings() {
        val hunters = getAllHunters().filter { it.isOnline && !it.isDead }
        val runners = getAllRunners().filter { it.isOnline && !it.isDead }
        
        for (runner in runners) {
            val runnerWorld = runner.world ?: continue
            
            val nearestHunter = hunters.minByOrNull { hunter ->
                val hunterWorld = hunter.world
                if (hunterWorld == null || hunterWorld != runnerWorld) {
                    Double.MAX_VALUE
                } else {
                    try {
                        hunter.location.distance(runner.location)
                    } catch (e: Exception) {
                        Double.MAX_VALUE
                    }
                }
            }
            
            nearestHunter?.let { hunter ->
                val hunterWorld = hunter.world
                if (hunterWorld != null && hunterWorld == runnerWorld) {
                    try {
                        val distance = hunter.location.distance(runner.location)
                        val chunks = (distance / 16).toInt()
                        
                        // クールダウンチェック
                        val currentTime = System.currentTimeMillis()
                        val lastWarningTime = proximityWarningCooldowns[runner.uniqueId] ?: 0L
                        val cooldownMillis = configManager.getProximityCooldown() * 1000L
                        
                        if (currentTime - lastWarningTime >= cooldownMillis) {
                            val warningMessage = when {
                                chunks <= configManager.getProximityLevel1() -> messageManager.getMessage("proximity.level-1")
                                chunks <= configManager.getProximityLevel2() -> messageManager.getMessage("proximity.level-2")
                                chunks <= configManager.getProximityLevel3() -> messageManager.getMessage("proximity.level-3")
                                else -> null
                            }
                            
                            warningMessage?.let { message ->
                                runner.sendMessage(message)
                                proximityWarningCooldowns[runner.uniqueId] = currentTime
                            }
                        }
                    } catch (e: Exception) {
                        // 距離計算に失敗した場合は警告をスキップ
                    }
                }
            }
        }
    }
    
    fun checkWinConditions() {
        if (gameState != GameState.RUNNING) return
        
        val aliveRunners = getAllRunners().filter { it.isOnline && !it.isDead }
        val deadRunnersCount = deadRunners.size
        val totalRunners = aliveRunners.size + deadRunnersCount
        
        val aliveHunters = getAllHunters().filter { it.isOnline && !it.isDead }
        
        when {
            // 全ランナーが死亡またはゲームから退出した場合（即座に終了）
            totalRunners == 0 || aliveRunners.isEmpty() -> {
                endGame(messageManager.getMessage("victory.hunter-elimination"))
            }
            // 全ハンターが死亡またはゲームから退出した場合
            aliveHunters.isEmpty() -> {
                endGame(messageManager.getMessage("victory.hunter-no-hunters"))
            }
        }
    }
    
    private fun checkWinConditionsAfterLeave(leftPlayerRole: PlayerRole?) {
        if (gameState != GameState.RUNNING) return
        
        val aliveRunners = getAllRunners().filter { it.isOnline && !it.isDead }
        val aliveHunters = getAllHunters().filter { it.isOnline && !it.isDead }
        
        when (leftPlayerRole) {
            PlayerRole.RUNNER -> {
                if (aliveRunners.isEmpty()) {
                    endGame(messageManager.getMessage("victory.hunter-runners-left"))
                }
            }
            PlayerRole.HUNTER -> {
                if (aliveHunters.isEmpty()) {
                    endGame(messageManager.getMessage("victory.runner-hunters-left"))
                }
            }
            else -> {
                // Spectatorの退出は勝利条件に影響しない
            }
        }
    }
    
    fun onEnderDragonDeath(killer: Player?) {
        if (gameState == GameState.RUNNING && killer != null && getPlayerRole(killer) == PlayerRole.RUNNER) {
            endGame(messageManager.getMessage("victory.runner-dragon"))
        }
    }
    
    private fun endGame(message: String) {
        gameState = GameState.ENDED
        
        // 勝利条件を特定して統計を終了
        val winCondition = determineWinCondition(message)
        val winningTeam = determineWinningTeam(message)
        
        if (winCondition != null && winningTeam != null) {
            gameStats.endGame(winningTeam, winCondition)
        }
        
        // UIにゲーム終了を通知
        try {
            plugin.getUIManager().showGameStateChange(GameState.ENDED)
        } catch (e: Exception) {
            plugin.logger.warning("UI通知でエラー: ${e.message}")
        }
        
        try {
            proximityTask?.cancel()
        } catch (e: Exception) {
            plugin.logger.warning("Error canceling proximity task: ${e.message}")
        } finally {
            proximityTask = null
        }
        
        try {
            plugin.getCompassTracker().stopTracking()
        } catch (e: Exception) {
            plugin.logger.warning("Error stopping compass tracking: ${e.message}")
        }
        
        try {
            plugin.getCurrencyTracker().stopTracking()
        } catch (e: Exception) {
            plugin.logger.warning("Error stopping currency tracking: ${e.message}")
        }
        
        // 新しいリザルト表示システムを使用
        try {
            gameResultManager.showGameResult(gameStats)
        } catch (e: Exception) {
            plugin.logger.warning("リザルト表示でエラー: ${e.message}")
            // フォールバック: 従来のメッセージ表示
            Bukkit.broadcastMessage(messageManager.getMessage("game.end"))
            Bukkit.broadcastMessage(message)
        }
        
        // Reset after 10 seconds
        try {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                resetGame()
            }, 200L)
        } catch (e: Exception) {
            plugin.logger.severe("Error scheduling game reset: ${e.message}")
            resetGame() // Immediate reset as fallback
        }
    }
    
    /**
     * 勝利条件を特定
     */
    private fun determineWinCondition(message: String): GameStats.WinCondition? {
        return when {
            message.contains("エンダードラゴン") -> GameStats.WinCondition.ENDER_DRAGON_KILLED
            message.contains("逃げる人を全員倒") -> GameStats.WinCondition.ALL_RUNNERS_ELIMINATED
            message.contains("追う人が全員退出") -> GameStats.WinCondition.ALL_HUNTERS_LEFT
            message.contains("逃げる人が全員退出") -> GameStats.WinCondition.ALL_RUNNERS_LEFT
            else -> null
        }
    }
    
    /**
     * 勝利チームを特定
     */
    private fun determineWinningTeam(message: String): PlayerRole? {
        return when {
            message.contains("ハンター") || message.contains("追う人") -> PlayerRole.HUNTER
            message.contains("ランナー") || message.contains("逃げる人") -> PlayerRole.RUNNER
            else -> null
        }
    }
    
    private fun resetGame() {
        gameState = GameState.WAITING
        
        // 全プレイヤーのゲームモードを元に戻す
        try {
            for (player in Bukkit.getOnlinePlayers()) {
                val originalMode = originalGameModes[player.uniqueId] ?: GameMode.SURVIVAL
                player.gameMode = originalMode
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error restoring player game modes: ${e.message}")
        }
        
        // タスクを安全に停止
        try {
            proximityTask?.cancel()
        } catch (e: Exception) {
            plugin.logger.warning("Error canceling proximity task during reset: ${e.message}")
        } finally {
            proximityTask = null
        }
        
        try {
            plugin.getCompassTracker().stopTracking()
        } catch (e: Exception) {
            plugin.logger.warning("Error stopping compass tracking during reset: ${e.message}")
        }
        
        try {
            plugin.getCurrencyTracker().stopTracking()
        } catch (e: Exception) {
            plugin.logger.warning("Error stopping currency tracking during reset: ${e.message}")
        }
        
        // チームを解散
        // UIManagerがスコアボードとチームを管理するため、ここでは不要
        // removeTeams()
        
        // データをクリア
        players.clear()
        fixedHunters.clear()
        disconnectedPlayers.clear()
        originalGameModes.clear()
        
        // リスポンタスクをクリア
        respawnTasks.values.forEach { it.cancel() }
        respawnTasks.clear()
        countdownTasks.values.forEach { it.cancel() }
        countdownTasks.clear()
        deadRunners.clear()
        
        // 近接警告のクールダウンをクリア
        proximityWarningCooldowns.clear()
        
        // ゲーム開始カウントダウンタスクをクリア
        countdownTask?.cancel()
        countdownTask = null
        
        Bukkit.broadcastMessage(messageManager.getMessage("game.reset"))
    }
    
    fun setMinPlayers(count: Int) {
        minPlayers = count
    }
    
    fun getMinPlayers(): Int = minPlayers
    
    fun getDeadRunnersCount(): Int = deadRunners.size
    
    // ======== ゲーム開始時の転送システム ========
    
    private fun teleportPlayersToStartPositions() {
        val hunters = getAllHunters().filter { it.isOnline }
        val runners = getAllRunners().filter { it.isOnline }
        val spectators = getAllSpectators().filter { it.isOnline }
        
        // ワールドを取得（デフォルトはオーバーワールド）
        val world = Bukkit.getWorlds().firstOrNull() ?: run {
            plugin.logger.severe("World not found!")
            return
        }
        
        try {
            // 既にスポーンした位置を記録
            val spawnedLocations = mutableListOf<Location>()
            
            // 各プレイヤーを500-1000ブロックの範囲でバラバラにスポーン
            val allPlayers = hunters + runners
            
            allPlayers.forEach { player ->
                try {
                    // 500-1000ブロックの範囲で他のプレイヤーから離れた場所を生成
                    val spawnLocation = generateRandomSpawnLocation(world, spawnedLocations, 500.0, 1000.0)
                    spawnedLocations.add(spawnLocation)
                    
                    player.teleport(spawnLocation)
                    player.gameMode = GameMode.SURVIVAL
                    
                    // 役割に応じたメッセージを送信
                    val role = getPlayerRole(player)
                    when (role) {
                        PlayerRole.HUNTER -> {
                            player.sendMessage(messageManager.getMessage(player, "game-start-role.hunter"))
                            plugin.logger.info("Hunter ${player.name} teleported to ${spawnLocation.blockX}, ${spawnLocation.blockY}, ${spawnLocation.blockZ}")
                        }
                        PlayerRole.RUNNER -> {
                            player.sendMessage(messageManager.getMessage(player, "game-start-role.runner"))
                            plugin.logger.info("Runner ${player.name} teleported to ${spawnLocation.blockX}, ${spawnLocation.blockY}, ${spawnLocation.blockZ}")
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Error teleporting player ${player.name}: ${e.message}")
                    // エラーの場合はワールドスポーンに転送
                    try {
                        player.teleport(world.spawnLocation)
                        player.gameMode = GameMode.SURVIVAL
                    } catch (ex: Exception) {
                        plugin.logger.severe("Critical error teleporting ${player.name}: ${ex.message}")
                    }
                }
            }
            
            // 観戦者はスペクテーターモードを継続
            spectators.forEach { spectator ->
                try {
                    spectator.gameMode = GameMode.SPECTATOR
                    spectator.sendMessage(messageManager.getMessage(spectator, "game-start-role.spectator"))
                } catch (e: Exception) {
                    plugin.logger.warning("Error setting spectator mode for ${spectator.name}: ${e.message}")
                }
            }
            
            Bukkit.broadcastMessage(messageManager.getMessage("game-management.players-teleported"))
            
        } catch (e: Exception) {
            plugin.logger.severe("Error during player teleportation: ${e.message}")
            // エラーの場合は全員をスペクテーターモードに設定
            (hunters + runners + spectators).forEach { player ->
                try {
                    player.gameMode = GameMode.SPECTATOR
                } catch (ex: Exception) {
                    plugin.logger.warning("Error in emergency mode setting: ${ex.message}")
                }
            }
        }
    }
    
    private fun generateRandomSpawnLocation(
        world: World, 
        existingLocations: List<Location> = emptyList(),
        minDistance: Double = 500.0,
        maxDistance: Double = 1000.0
    ): Location {
        val maxAttempts = 100
        var attempts = 0
        val minDistanceBetweenPlayers = 100.0 // プレイヤー間の最小距離
        
        while (attempts < maxAttempts) {
            attempts++
            
            // 500-1000ブロックの範囲でランダムな角度と距離を生成
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val distance = Random.nextDouble(minDistance, maxDistance)
            
            // 極座標から直交座標に変換
            val x = distance * cos(angle)
            val z = distance * sin(angle)
            
            // 安全な高度を見つける
            val safeY = findSafeY(world, x.toInt(), z.toInt())
            
            if (safeY > 0) {
                val location = Location(world, x, safeY.toDouble(), z)
                
                // 他のプレイヤーとの距離をチェック
                var tooClose = false
                for (existing in existingLocations) {
                    if (location.distance(existing) < minDistanceBetweenPlayers) {
                        tooClose = true
                        break
                    }
                }
                
                if (!tooClose) {
                    plugin.logger.info("Safe teleport location generated: ${x.toInt()}, $safeY, ${z.toInt()} (distance: ${distance.toInt()}m, attempts: $attempts)")
                    return location
                }
            }
        }
        
        // フォールバック: 角度を変えて再試行
        plugin.logger.warning("Difficult to find isolated location, using fallback.")
        val fallbackAngle = Random.nextDouble(0.0, 2 * Math.PI)
        val fallbackDistance = Random.nextDouble(minDistance, maxDistance)
        val fallbackX = fallbackDistance * cos(fallbackAngle)
        val fallbackZ = fallbackDistance * sin(fallbackAngle)
        val fallbackY = world.getHighestBlockYAt(fallbackX.toInt(), fallbackZ.toInt()) + 2
        
        return Location(world, fallbackX, fallbackY.toDouble(), fallbackZ)
    }
    
    private fun findSafeY(world: World, x: Int, z: Int): Int {
        // 地上から少し上の安全な場所を探す
        for (y in world.maxHeight - 1 downTo 1) {
            val block = world.getBlockAt(x, y, z)
            val blockBelow = world.getBlockAt(x, y - 1, z)
            val blockAbove = world.getBlockAt(x, y + 1, z)
            
            // 足場があり、プレイヤーが立てる空間がある場所
            if (!blockBelow.type.isAir && 
                block.type.isAir && 
                blockAbove.type.isAir && 
                !blockBelow.type.name.contains("LAVA") &&
                !blockBelow.type.name.contains("WATER")) {
                return y
            }
        }
        
        // 見つからない場合は高い位置に配置
        return world.getHighestBlockYAt(x, z) + 2
    }
    
    
    // ======== インベントリ管理システム ========
    
    /**
     * 全プレイヤーのインベントリをクリア
     */
    private fun clearAllPlayerInventories() {
        val hunters = getAllHunters().filter { it.isOnline }
        val runners = getAllRunners().filter { it.isOnline }
        val spectators = getAllSpectators().filter { it.isOnline }
        
        (hunters + runners + spectators).forEach { player ->
            try {
                // インベントリとアーマーをクリア
                player.inventory.clear()
                player.inventory.setArmorContents(arrayOfNulls(4))
                
                // 体力とハンガーをリセット
                player.health = player.maxHealth
                player.foodLevel = 20
                player.saturation = 5.0f
                player.exhaustion = 0.0f
                
                // エフェクトをクリア
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }
                
                // 経験値をリセット
                player.level = 0
                player.exp = 0.0f
                player.totalExperience = 0
                
                plugin.logger.info("Cleared inventory for player ${player.name}")
                
            } catch (e: Exception) {
                plugin.logger.warning("Error clearing inventory for player ${player.name}: ${e.message}")
            }
        }
        
        Bukkit.broadcastMessage(messageManager.getMessage("game-management.inventory-cleared"))
    }
    
    // ======== 死亡・リスポン管理システム ========
    
    fun onPlayerDeath(player: Player) {
        if (gameState != GameState.RUNNING) return
        
        val role = getPlayerRole(player) ?: return
        
        when (role) {
            PlayerRole.HUNTER -> {
                // ハンターは即座リスポン
                if (configManager.isHunterInstantRespawn()) {
                    handleHunterRespawn(player)
                }
            }
            PlayerRole.RUNNER -> {
                // ランナーは死亡処理とリスポンタイマー開始
                handleRunnerDeath(player)
            }
            PlayerRole.SPECTATOR -> {
                // 観戦者は何もしない
            }
        }
    }
    
    private fun handleHunterRespawn(player: Player) {
        try {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (player.isOnline && gameState == GameState.RUNNING) {
                    player.spigot().respawn()
                    player.sendMessage(messageManager.getMessage(player, "respawn-system.hunter-respawned"))
                    plugin.logger.info("Hunter ${player.name} respawned instantly")
                }
            }, 1L) // 1tick後にリスポン
        } catch (e: Exception) {
            plugin.logger.warning("Error in hunter respawn process for ${player.name}: ${e.message}")
        }
    }
    
    private fun handleRunnerDeath(player: Player) {
        val currentTime = System.currentTimeMillis()
        deadRunners[player.uniqueId] = currentTime
        
        val respawnTime = configManager.getRunnerRespawnTime()
        
        // 既存のタスクをキャンセル
        respawnTasks[player.uniqueId]?.cancel()
        countdownTasks[player.uniqueId]?.cancel()
        
        // 死亡メッセージ
        player.sendMessage(messageManager.getMessage(player, "respawn-system.runner-death", mapOf("time" to respawnTime)))
        Bukkit.broadcastMessage(messageManager.getMessage("respawn-system.runner-death-broadcast", mapOf("player" to player.name, "time" to respawnTime)))
        
        // リスポン待ち中はスペクテーターモードに変更
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            try {
                if (player.isOnline && gameState == GameState.RUNNING && deadRunners.containsKey(player.uniqueId)) {
                    player.gameMode = GameMode.SPECTATOR
                    player.sendMessage(messageManager.getMessage(player, "respawn-system.waiting-spectator"))
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error setting spectator mode for runner ${player.name}: ${e.message}")
            }
        }, 20L) // 1秒後に設定（リスポン画面の後）
        
        // カウントダウン表示タスクを開始
        startRespawnCountdown(player, respawnTime)
        
        // リスポンタスクをスケジュール
        val respawnTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    if (player.isOnline && gameState == GameState.RUNNING) {
                        // タスクとデータをクリア
                        deadRunners.remove(player.uniqueId)
                        respawnTasks.remove(player.uniqueId)
                        countdownTasks[player.uniqueId]?.cancel()
                        countdownTasks.remove(player.uniqueId)
                        
                        player.spigot().respawn()
                        
                        // サバイバルモードに戻す
                        player.gameMode = GameMode.SURVIVAL
                        
                        player.sendMessage(messageManager.getMessage(player, "respawn-system.runner-respawned"))
                        Bukkit.broadcastMessage(messageManager.getMessage("respawn-system.runner-respawned-broadcast", mapOf("player" to player.name)))
                        
                        // UIManager経由でタイトルクリア
                        try {
                            plugin.getUIManager().showTitle(player, "§a✓ リスポン完了", "§fエンダードラゴンを倒そう！", 10, 30, 10)
                        } catch (e: Exception) {
                            plugin.logger.warning("UI表示でエラー: ${e.message}")
                        }
                        
                        plugin.logger.info("Runner ${player.name} respawned after ${respawnTime} seconds")
                        
                        // リスポン後に勝利条件をチェック
                        checkWinConditions()
                    } else {
                        // プレイヤーがオフラインまたはゲーム終了の場合
                        deadRunners.remove(player.uniqueId)
                        respawnTasks.remove(player.uniqueId)
                        countdownTasks[player.uniqueId]?.cancel()
                        countdownTasks.remove(player.uniqueId)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Error in runner respawn process for ${player.name}: ${e.message}")
                    deadRunners.remove(player.uniqueId)
                    respawnTasks.remove(player.uniqueId)
                    countdownTasks[player.uniqueId]?.cancel()
                    countdownTasks.remove(player.uniqueId)
                }
            }
        }
        
        respawnTasks[player.uniqueId] = respawnTask
        respawnTask.runTaskLater(plugin, (respawnTime * 20).toLong()) // 秒をtickに変換
        
        // 死亡直後に勝利条件をチェック（遅延実行）
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            checkWinConditions()
            
            // ゲームが終了した場合、リスポンタスクをキャンセル
            if (gameState == GameState.ENDED) {
                respawnTasks[player.uniqueId]?.cancel()
                respawnTasks.remove(player.uniqueId)
                countdownTasks[player.uniqueId]?.cancel()
                countdownTasks.remove(player.uniqueId)
                deadRunners.remove(player.uniqueId)
            }
        }, 5L) // 0.25秒後にチェック
    }
    
    private fun startRespawnCountdown(player: Player, totalTime: Int) {
        var remainingTime = totalTime
        
        val countdownTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    if (!player.isOnline || gameState != GameState.RUNNING || !deadRunners.containsKey(player.uniqueId)) {
                        cancel()
                        countdownTasks.remove(player.uniqueId)
                        return
                    }
                    
                    if (remainingTime > 0) {
                        // タイトルでカウントダウン表示
                        val title = messageManager.getMessage(player, "respawn-system.death-title")
                        val subtitle = messageManager.getMessage(player, "respawn-system.death-subtitle", mapOf("time" to remainingTime))
                        
                        try {
                            plugin.getUIManager().showTitle(player, title, subtitle, 0, 25, 0)
                        } catch (e: Exception) {
                            // フォールバック: チャットメッセージ
                            player.sendMessage(messageManager.getMessage(player, "respawn-system.countdown-chat", mapOf("time" to remainingTime)))
                        }
                        
                        // 最後の3秒は音とメッセージで強調
                        if (remainingTime <= 3) {
                            try {
                                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (3 - remainingTime) * 0.2f)
                            } catch (e: Exception) {
                                // 音再生エラーは無視
                            }
                            player.sendMessage(messageManager.getMessage(player, "respawn-system.countdown-emphasis", mapOf("time" to remainingTime)))
                        }
                        
                        remainingTime--
                    } else {
                        // カウントダウン終了
                        cancel()
                        countdownTasks.remove(player.uniqueId)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Error in countdown display: ${e.message}")
                    cancel()
                    countdownTasks.remove(player.uniqueId)
                }
            }
        }
        
        countdownTasks[player.uniqueId] = countdownTask
        countdownTask.runTaskTimer(plugin, 0L, 20L) // 1秒間隔で実行
    }
    
    // ======== ゲーム開始カウントダウンシステム ========
    
    private fun startGameCountdown() {
        val countdownSeconds = configManager.getStartCountdown()
        var remainingTime = countdownSeconds
        
        // カウントダウン開始メッセージ
        Bukkit.broadcastMessage(messageManager.getMessage("game-management.start-countdown", mapOf("time" to countdownSeconds)))
        
        // 既存のカウントダウンタスクをキャンセル
        countdownTask?.cancel()
        
        countdownTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    if (gameState != GameState.STARTING) {
                        cancel()
                        return
                    }
                    
                    if (remainingTime > 0) {
                        // タイトルとサウンドでカウントダウン表示
                        val title = messageManager.getMessage("game-management.start-title")
                        val subtitle = messageManager.getMessage("game-management.start-subtitle", mapOf("time" to remainingTime))
                        
                        Bukkit.getOnlinePlayers().forEach { player ->
                            try {
                                plugin.getUIManager().showTitle(player, title, subtitle, 0, 25, 0)
                                
                                // カウントダウン音
                                if (remainingTime <= 5) {
                                    val pitch = 1.0f + (5 - remainingTime) * 0.2f
                                    player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch)
                                }
                            } catch (e: Exception) {
                                // 音やタイトル表示エラーは無視
                            }
                        }
                        
                        // 最後の5秒はチャットでも表示
                        if (remainingTime <= 5) {
                            Bukkit.broadcastMessage(messageManager.getMessage("game-management.countdown-final", mapOf("time" to remainingTime)))
                        }
                        
                        remainingTime--
                    } else {
                        // カウントダウン終了 - 実際のゲーム開始
                        cancel()
                        actuallyStartGame()
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Error in countdown: ${e.message}")
                    cancel()
                    actuallyStartGame() // エラー時も強制開始
                }
            }
        }
        
        countdownTask?.runTaskTimer(plugin, 20L, 20L) // 1秒後から1秒間隔で実行
    }
    
    private fun actuallyStartGame() {
        // プレイヤーのインベントリをクリア（ゲーム開始前のアイテムを削除）
        clearAllPlayerInventories()
        
        // ハンターとランナーをランダムな場所に転送し、サバイバルモードに設定
        teleportPlayersToStartPositions()
        
        gameState = GameState.RUNNING
        
        // ゲーム統計を初期化して開始
        gameStats.startGame()
        
        // 全プレイヤーを統計に追加
        players.values.forEach { manhuntPlayer ->
            try {
                val player = Bukkit.getPlayer(manhuntPlayer.player.uniqueId)
                if (player != null && player.isOnline) {
                    gameStats.addPlayer(player, manhuntPlayer.role)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error adding player statistics: ${e.message}")
            }
        }
        
        // UIにゲーム実行状態を通知
        try {
            plugin.getUIManager().showGameStateChange(GameState.RUNNING)
        } catch (e: Exception) {
            plugin.logger.warning("UI通知でエラー: ${e.message}")
        }
        
        // Broadcast game start
        Bukkit.broadcastMessage(messageManager.getMessage("game.start"))
        Bukkit.broadcastMessage(messageManager.getMessage("game-start-role.runner"))
        Bukkit.broadcastMessage(messageManager.getMessage("game-start-role.hunter"))
        Bukkit.broadcastMessage(messageManager.getMessage("game-start-role.spectator"))
        
        // チームを作成してプレイヤーを割り当て
        // UIManagerがスコアボードとチームを管理するため、ここでは不要
        // createTeams()
        // assignPlayersToTeams()
        
        // Start proximity checking
        startProximityChecking()
        
        // Start compass tracking
        plugin.getCompassTracker().startTracking()
        
        // Start currency tracking
        plugin.getCurrencyTracker().startTracking()
        
        // Reset economy for all players
        plugin.getEconomyManager().resetAllBalances()
        plugin.getShopManager().resetAllPurchases()
        
        // ハンターに仮想コンパスの使い方を自動通知
        getAllHunters().forEach { hunter ->
            if (hunter.isOnline) {
                try {
                    plugin.getCompassTracker().giveCompass(hunter)
                } catch (e: Exception) {
                    plugin.logger.warning("Error explaining compass to hunter: ${e.message}")
                }
            }
        }
        
        // 開始完了タイトル
        Bukkit.getOnlinePlayers().forEach { player ->
            try {
                val roleSpecificMessage = when (getPlayerRole(player)) {
                    PlayerRole.HUNTER -> messageManager.getMessage(player, "game.hunter-start")
                    PlayerRole.RUNNER -> messageManager.getMessage(player, "game.runner-start")
                    PlayerRole.SPECTATOR -> messageManager.getMessage(player, "game.spectator-start")
                    null -> messageManager.getMessage(player, "game.start")
                }
                val startTitle = messageManager.getMessage(player, "game.start")
                plugin.getUIManager().showTitle(player, startTitle, roleSpecificMessage, 10, 40, 10)
            } catch (e: Exception) {
                // タイトル表示エラーは無視
            }
        }
    }
    
}