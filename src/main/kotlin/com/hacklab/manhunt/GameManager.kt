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
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemFlag
import org.bukkit.enchantments.Enchantment
import org.bukkit.Sound

class GameManager(private val plugin: Main, val configManager: ConfigManager, private val messageManager: MessageManager) {
    private var gameState = GameState.WAITING
    private val players = mutableMapOf<UUID, ManhuntPlayer>()
    private val fixedHunters = mutableSetOf<UUID>()
    private var minPlayers = configManager.getMinPlayers()
    private var proximityTask: BukkitRunnable? = null
    private val currentProximityWarnings = mutableMapOf<UUID, String?>()
    private var resetCountdownTask: BukkitRunnable? = null
    private var nightSkipTask: BukkitRunnable? = null
    
    // 統計とリザルト管理
    private val gameStats = GameStats()
    private lateinit var gameResultManager: GameResultManager
    
    // スポーン管理
    private var spawnManager: SpawnManager? = null
    
    fun getPlugin(): Main = plugin
    
    fun setSpawnManager(manager: SpawnManager) {
        spawnManager = manager
    }
    
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
    private val customRespawnTimes = mutableMapOf<UUID, Int>() // プレイヤーID -> カスタムリスポン時間
    
    // ゲーム開始時刻
    private var gameStartTime: Long = 0
    
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
                Bukkit.broadcastMessage(messageManager.getMessage("game-management.player-left-spectator", "player" to player.name))
            } else {
                // 切断の場合は元の役割を保存（ネットワークエラーの可能性）
                playerRole?.let { role ->
                    disconnectedPlayers[player.uniqueId] = role
                    players.remove(player.uniqueId)
                    fixedHunters.remove(player.uniqueId)
                    Bukkit.broadcastMessage(messageManager.getMessage("game-management.player-disconnected", "player" to player.name))
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
            
            player.sendMessage(messageManager.getMessage(player, "game-management.network-recovery", "role" to roleText))
            Bukkit.broadcastMessage(messageManager.getMessage("game-management.network-recovery-broadcast", "player" to player.name))
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
        
        // UIManagerに変更を通知してスコアボードを更新
        plugin.getUIManager().updateScoreboardForAllPlayers()
        
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
        
        // スタート条件をチェック（待機中のみ）
        if (gameState == GameState.WAITING) {
            checkStartConditions()
        }
        
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
                plugin.logger.info("Start conditions met!")
                
                // 自動スタートが有効な場合のみゲーム開始
                if (configManager.isAutoStartEnabled()) {
                    plugin.logger.info("Auto-start is enabled. Starting game.")
                    startGame()
                } else {
                    plugin.logger.info("Auto-start is disabled. Game will not start automatically.")
                }
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
    
    fun forceEndGame() {
        if (gameState == GameState.RUNNING || gameState == GameState.STARTING) {
            plugin.logger.info("Game force-ended by admin")
            endGame("admin-force-stop") // 管理者による強制終了
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
    
    private fun startNightSkipMonitoring() {
        try {
            nightSkipTask?.cancel()
        } catch (e: Exception) {
            plugin.logger.warning("Error canceling existing night skip task: ${e.message}")
        }
        
        nightSkipTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    if (gameState != GameState.RUNNING) {
                        cancel()
                        return
                    }
                    
                    // 全てのワールドをチェック
                    Bukkit.getWorlds().forEach { world ->
                        val currentTime = world.time
                        // Minecraftの時間: 0=朝6時, 6000=正午, 12000=夕方, 18000=夜0時
                        // 夜は12542〜23460 (夕暮れから夜明けまで)
                        // 18000=真夜中、23000=夜明け2分前
                        // 真夜中を過ぎたらスキップ（約6分間の夜を体験）
                        if (currentTime in 18000..22999) {
                            world.time = 23000 // 夜明け2分前に設定
                            plugin.logger.info("Skipped night in world ${world.name}: time set to dawn minus 2 minutes")
                            
                            // プレイヤーに通知
                            world.players.forEach { player ->
                                if (getPlayerRole(player) != null && getPlayerRole(player) != PlayerRole.SPECTATOR) {
                                    player.sendMessage(messageManager.getMessage(player, "game-management.night-skipped"))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Error in night skip monitoring: ${e.message}")
                }
            }
        }
        nightSkipTask?.runTaskTimer(plugin, 0L, 100L) // 5秒ごとにチェック（100 ticks = 5秒）
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
            
            if (nearestHunter == null) {
                // ハンターが近くにいない場合は警告をクリア
                currentProximityWarnings[runner.uniqueId] = null
                continue
            }
            
            val hunterWorld = nearestHunter.world
            if (hunterWorld != null && hunterWorld == runnerWorld) {
                try {
                    val distance = nearestHunter.location.distance(runner.location)
                    val chunks = (distance / 16).toInt()
                    
                    val warningMessage = when {
                        chunks <= configManager.getProximityLevel1() -> messageManager.getMessage(runner, "proximity.level-1")
                        chunks <= configManager.getProximityLevel2() -> messageManager.getMessage(runner, "proximity.level-2")
                        chunks <= configManager.getProximityLevel3() -> messageManager.getMessage(runner, "proximity.level-3")
                        else -> null
                    }
                    
                    // 警告メッセージを保存（常に更新）
                    currentProximityWarnings[runner.uniqueId] = warningMessage
                    
                } catch (e: Exception) {
                    // 距離計算に失敗した場合は警告をクリア
                    currentProximityWarnings[runner.uniqueId] = null
                }
            } else {
                // 異なるワールドの場合は警告をクリア
                currentProximityWarnings[runner.uniqueId] = null
            }
        }
        
        // 死亡したランナーの警告をクリア
        currentProximityWarnings.keys.toList().forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player == null || !player.isOnline || getPlayerRole(player) != PlayerRole.RUNNER || isRunnerDead(player)) {
                currentProximityWarnings.remove(uuid)
            }
        }
    }
    
    fun checkWinConditions() {
        if (gameState != GameState.RUNNING) return
        
        val allRunners = getAllRunners().filter { it.isOnline }
        val aliveRunners = allRunners.filter { !isRunnerDead(it) }
        val deadRunnersCount = allRunners.count { isRunnerDead(it) }
        
        val aliveHunters = getAllHunters().filter { it.isOnline && !it.isDead }
        
        when {
            // 全ランナーが死亡またはゲームから退出した場合（即座に終了）
            allRunners.isEmpty() || (aliveRunners.isEmpty() && deadRunnersCount > 0) -> {
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
        
        val aliveRunners = getAllRunners().filter { it.isOnline && !isRunnerDead(it) }
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
        
        // バディーシステムのクリーンアップ
        try {
            plugin.getBuddySystem().onGameEnd()
        } catch (e: Exception) {
            plugin.logger.warning("バディーシステムのクリーンアップでエラー: ${e.message}")
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
            nightSkipTask?.cancel()
        } catch (e: Exception) {
            plugin.logger.warning("Error canceling night skip task: ${e.message}")
        } finally {
            nightSkipTask = null
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
        
        // WarpCommandのクリーンアップ
        (plugin.getCommand("warp")?.getExecutor() as? WarpCommand)?.onGameEnd()
        
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
        // すべてのプレイヤーをスペクテイターモードに設定
        Bukkit.getOnlinePlayers().forEach { player ->
            player.gameMode = GameMode.SPECTATOR
        }
        
        // 5分後にリセット（カウントダウン付き）
        startResetCountdown()
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
        
        // 全プレイヤーをアドベンチャーモードに設定し、お疲れ様メッセージを表示
        try {
            for (player in Bukkit.getOnlinePlayers()) {
                player.gameMode = GameMode.ADVENTURE
                
                // お疲れ様メッセージをタイトルで表示
                val title = messageManager.getMessage(player, "game.end-title")
                val subtitle = messageManager.getMessage(player, "game.end-subtitle")
                player.sendTitle(title, subtitle, 10, 60, 20)
                
                // ボスバーをクリア
                plugin.getUIManager().removeBossBar(player)
                plugin.getUIManager().removeResetCountdownBossBar(player)
                
                // インベントリをクリアしてロール変更アイテムを付与
                player.inventory.clear()
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    giveRoleChangeItem(player)
                }, 20L)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error setting up post-game state: ${e.message}")
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
        currentProximityWarnings.clear()
        
        // リスポンタスクをクリア
        respawnTasks.values.forEach { it.cancel() }
        respawnTasks.clear()
        countdownTasks.values.forEach { it.cancel() }
        countdownTasks.clear()
        deadRunners.clear()
        customRespawnTimes.clear()
        
        // 近接警告のクールダウンをクリア
        proximityWarningCooldowns.clear()
        
        // ゲーム開始カウントダウンタスクをクリア
        countdownTask?.cancel()
        countdownTask = null
        
        // 経済・ショップシステムをリセット
        try {
            plugin.getEconomyManager().resetAllBalances()
            plugin.getShopManager().resetAllPurchases()
        } catch (e: Exception) {
            plugin.logger.warning("Error resetting economy during game reset: ${e.message}")
        }
        
        Bukkit.broadcastMessage(messageManager.getMessage("game.reset"))
    }
    
    fun setMinPlayers(count: Int) {
        minPlayers = count
    }
    
    fun getMinPlayers(): Int = minPlayers
    
    private fun removeRoleChangeItems() {
        Bukkit.getOnlinePlayers().forEach { player ->
            player.inventory.contents.forEachIndexed { index, itemStack ->
                if (itemStack != null && isRoleChangeItem(itemStack)) {
                    player.inventory.setItem(index, null)
                }
            }
        }
    }
    
    fun getDeadRunnersCount(): Int = deadRunners.size
    
    fun isRunnerDead(player: Player): Boolean {
        return deadRunners.containsKey(player.uniqueId)
    }
    
    fun getDeadRunners(): List<Player> {
        return deadRunners.keys.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }
    }
    
    fun getProximityWarningForRunner(runner: Player): String? {
        return currentProximityWarnings[runner.uniqueId]
    }
    
    fun getRespawnTimeForPlayer(player: Player): Int {
        val deathTime = deadRunners[player.uniqueId] ?: return 0
        val respawnTime = customRespawnTimes[player.uniqueId] ?: configManager.getRunnerRespawnTime()
        val elapsedTime = (System.currentTimeMillis() - deathTime) / 1000
        val remainingTime = respawnTime - elapsedTime.toInt()
        return if (remainingTime > 0) remainingTime else 0
    }
    
    fun setCustomRespawnTime(player: Player, seconds: Int) {
        if (seconds <= 0) {
            customRespawnTimes.remove(player.uniqueId)
        } else {
            customRespawnTimes[player.uniqueId] = seconds
        }
        
        // 既に死亡中の場合は、リスポンタスクを更新
        if (deadRunners.containsKey(player.uniqueId) && gameState == GameState.RUNNING) {
            // 既存のタスクをキャンセル
            respawnTasks[player.uniqueId]?.cancel()
            countdownTasks[player.uniqueId]?.cancel()
            
            // 新しいリスポン時間で再スケジュール
            val currentTime = System.currentTimeMillis()
            val deathTime = deadRunners[player.uniqueId]!!
            val elapsedTime = ((currentTime - deathTime) / 1000).toInt()
            val newRemainingTime = seconds - elapsedTime
            
            if (newRemainingTime > 0) {
                // 新しいタスクをスケジュール
                scheduleRunnerRespawn(player, newRemainingTime)
            } else {
                // 即座にリスポン
                deadRunners.remove(player.uniqueId)
                respawnTasks.remove(player.uniqueId)
                countdownTasks.remove(player.uniqueId)
                
                player.spigot().respawn()
                player.gameMode = GameMode.SURVIVAL
                player.sendMessage(messageManager.getMessage(player, "respawn-system.runner-respawned"))
                
                plugin.getUIManager().removeBossBar(player)
            }
        }
    }
    
    private fun scheduleRunnerRespawn(player: Player, respawnTime: Int) {
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
                        Bukkit.broadcastMessage(messageManager.getMessage("respawn-system.runner-respawned-broadcast", "player" to player.name))
                        
                        // UIManager経由でタイトルクリアとボスバー削除
                        try {
                            plugin.getUIManager().showTitle(player, "§a✓ リスポン完了", "§fエンダードラゴンを倒そう！", 10, 30, 10)
                            plugin.getUIManager().removeBossBar(player)
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
        respawnTask.runTaskLater(plugin, (respawnTime * 20).toLong())
    }
    
    fun getGameElapsedTime(): Int {
        return if (gameState == GameState.RUNNING && gameStartTime > 0) {
            ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
        } else {
            0
        }
    }
    
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
            // SpawnManagerを使用してスポーン位置を生成
            val spawnLocations = if (spawnManager != null) {
                plugin.logger.info("Using SpawnManager for player spawn placement")
                spawnManager!!.generateSpawnLocations(world)
            } else {
                plugin.logger.warning("SpawnManager not available, using legacy spawn system")
                // レガシーシステムを使用
                val locations = mutableMapOf<Player, Location>()
                val spawnedLocations = mutableListOf<Location>()
                
                (hunters + runners).forEach { player ->
                    val role = getPlayerRole(player)
                    val spawnLocation = if (role == PlayerRole.RUNNER) {
                        generateRandomSpawnLocation(world, spawnedLocations, 500.0, 1000.0, avoidOcean = true)
                    } else {
                        generateRandomSpawnLocation(world, spawnedLocations, 500.0, 1000.0, avoidOcean = false)
                    }
                    spawnedLocations.add(spawnLocation)
                    locations[player] = spawnLocation
                }
                locations
            }
            
            // 各プレイヤーをスポーン位置に転送
            (hunters + runners).forEach { player ->
                try {
                    val spawnLocation = spawnLocations[player] ?: world.spawnLocation
                    val role = getPlayerRole(player)
                    
                    player.teleport(spawnLocation)
                    player.gameMode = GameMode.SURVIVAL
                    
                    // 役割に応じたメッセージを送信と初期装備配布
                    when (role) {
                        PlayerRole.HUNTER -> {
                            player.sendMessage(messageManager.getMessage(player, "game-start-role.hunter"))
                            giveStartingItems(player, PlayerRole.HUNTER)
                            giveShopItemToPlayer(player)
                            plugin.logger.info("Hunter ${player.name} teleported to ${spawnLocation.blockX}, ${spawnLocation.blockY}, ${spawnLocation.blockZ}")
                        }
                        PlayerRole.RUNNER -> {
                            player.sendMessage(messageManager.getMessage(player, "game-start-role.runner"))
                            giveStartingItems(player, PlayerRole.RUNNER)
                            giveShopItemToPlayer(player)
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
        maxDistance: Double = 1000.0,
        avoidOcean: Boolean = false
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
                    // 海を避ける場合のチェック
                    if (avoidOcean) {
                        val biome = world.getBiome(x.toInt(), safeY, z.toInt())
                        if (biome.name.contains("OCEAN") || biome.name.contains("RIVER")) {
                            continue // 海または川の場合はスキップ
                        }
                    }
                    
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
            } catch (e: Exception) {
                plugin.logger.warning("Failed to clear inventory for ${player.name}: ${e.message}")
            }
        }
    }
    
    private fun resetPlayerAdvancements() {
        val hunters = getAllHunters().filter { it.isOnline }
        val runners = getAllRunners().filter { it.isOnline }
        
        (hunters + runners).forEach { player ->
            try {
                // 全ての実績をリセット
                val advancementIterator = Bukkit.advancementIterator()
                while (advancementIterator.hasNext()) {
                    val advancement = advancementIterator.next()
                    val progress = player.getAdvancementProgress(advancement)
                    
                    // 達成済みの条件をすべて取り消し
                    progress.awardedCriteria.forEach { criterion ->
                        progress.revokeCriteria(criterion)
                    }
                }
                
                plugin.logger.info("Reset advancements for ${player.name}")
                player.sendMessage(messageManager.getMessage(player, "game-management.advancements-reset"))
            } catch (e: Exception) {
                plugin.logger.warning("Failed to reset advancements for ${player.name}: ${e.message}")
            }
        }
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
        
        val respawnTime = customRespawnTimes[player.uniqueId] ?: configManager.getRunnerRespawnTime()
        
        // 既存のタスクをキャンセル
        respawnTasks[player.uniqueId]?.cancel()
        countdownTasks[player.uniqueId]?.cancel()
        
        // 死亡メッセージ
        player.sendMessage(messageManager.getMessage(player, "respawn-system.runner-death", "time" to respawnTime))
        Bukkit.broadcastMessage(messageManager.getMessage("respawn-system.runner-death-broadcast", "player" to player.name, "time" to respawnTime))
        
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
                        Bukkit.broadcastMessage(messageManager.getMessage("respawn-system.runner-respawned-broadcast", "player" to player.name))
                        
                        // UIManager経由でタイトルクリアとボスバー削除
                        try {
                            plugin.getUIManager().showTitle(player, "§a✓ リスポン完了", "§fエンダードラゴンを倒そう！", 10, 30, 10)
                            plugin.getUIManager().removeBossBar(player)
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
                        val subtitle = messageManager.getMessage(player, "respawn-system.death-subtitle", "time" to remainingTime)
                        
                        try {
                            plugin.getUIManager().showTitle(player, title, subtitle, 0, 25, 0)
                            // ボスバーも更新
                            val totalTime = configManager.getRunnerRespawnTime()
                            plugin.getUIManager().showRespawnBossBar(player, remainingTime, totalTime)
                        } catch (e: Exception) {
                            // フォールバック: チャットメッセージ
                            player.sendMessage(messageManager.getMessage(player, "respawn-system.countdown-chat", "time" to remainingTime))
                        }
                        
                        // 最後の3秒は音とメッセージで強調
                        if (remainingTime <= 3) {
                            try {
                                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (3 - remainingTime) * 0.2f)
                            } catch (e: Exception) {
                                // 音再生エラーは無視
                            }
                            player.sendMessage(messageManager.getMessage(player, "respawn-system.countdown-emphasis", "time" to remainingTime))
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
        Bukkit.broadcastMessage(messageManager.getMessage("game-management.start-countdown", "time" to countdownSeconds))
        
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
                        val subtitle = messageManager.getMessage("game-management.start-subtitle", "time" to remainingTime)
                        
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
                            Bukkit.broadcastMessage(messageManager.getMessage("game-management.countdown-final", "time" to remainingTime))
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
        
        // 実績リセット（設定で有効な場合）
        if (configManager.shouldResetAdvancements()) {
            resetPlayerAdvancements()
        }
        
        // 時間を朝に設定
        plugin.logger.info("Setting time to day for all worlds...")
        Bukkit.getWorlds().forEach { world ->
            val oldTime = world.fullTime
            // setFullTimeを使用してより確実に時間を設定
            world.fullTime = 0 // 朝の時間（マインクラフトでは0が朝6時）
            world.time = 0
            
            // gameruleでdoDaylightCycleを確認
            val daylightCycle = world.getGameRuleValue(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE)
            plugin.logger.info("World ${world.name}: time changed from $oldTime to ${world.fullTime}, doDaylightCycle=$daylightCycle")
            
            // コマンドでも時間を設定（より確実）
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "time set day ${world.name}")
        }
        
        // ハンターとランナーをランダムな場所に転送し、サバイバルモードに設定
        teleportPlayersToStartPositions()
        
        gameState = GameState.RUNNING
        gameStartTime = System.currentTimeMillis()
        
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
        
        // バディーシステムの初期化
        try {
            plugin.getBuddySystem().onGameStart()
        } catch (e: Exception) {
            plugin.logger.warning("バディーシステムの初期化でエラー: ${e.message}")
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
        
        // Start night skip monitoring if enabled
        if (configManager.isNightSkipEnabled()) {
            startNightSkipMonitoring()
        }
        
        // Reset economy for all players
        plugin.getEconomyManager().resetAllBalances()
        plugin.getShopManager().resetAllPurchases()
        
        // ロール変更アイテムを削除
        removeRoleChangeItems()
        
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
    
    // ======== 初期装備配布システム ========
    
    private fun giveStartingItems(player: Player, role: PlayerRole) {
        val items = configManager.getStartingItems(role)
        
        items.forEach { itemString ->
            try {
                val parts = itemString.split(":")
                if (parts.size == 2) {
                    val material = Material.valueOf(parts[0])
                    val amount = parts[1].toInt()
                    
                    val itemStack = ItemStack(material, amount)
                    player.inventory.addItem(itemStack)
                }
            } catch (e: Exception) {
                plugin.logger.warning("初期装備の解析エラー: $itemString - ${e.message}")
            }
        }
    }
    
    private fun giveShopItemToPlayer(player: Player) {
        // 設定で無効化されている場合は配布しない
        if (!configManager.isShopItemEnabled()) {
            return
        }
        
        // プレイヤーの個人設定を確認
        if (!plugin.getShopManager().getShowShopItemPreference(player)) {
            return
        }
        
        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta!!
        meta.setDisplayName(messageManager.getMessage(player, "item.shop.name"))
        meta.lore = listOf(
            messageManager.getMessage(player, "item.shop.lore1"),
            messageManager.getMessage(player, "item.shop.lore2")
        )
        // アイテムを光らせる
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        
        // スロット7に配置（コンパスはスロット8）
        player.inventory.setItem(7, item)
    }
    
    // ======== ロール変更アイテム管理 ========
    
    private fun giveRoleChangeItem(player: Player) {
        // 既に持っているかチェック
        if (player.inventory.contents.any { it != null && isRoleChangeItem(it) }) {
            return
        }
        
        val item = ItemStack(Material.WRITABLE_BOOK)
        val meta = item.itemMeta!!
        meta.setDisplayName(messageManager.getMessage(player, "item.role-change.name"))
        meta.lore = listOf(
            messageManager.getMessage(player, "item.role-change.lore1"),
            messageManager.getMessage(player, "item.role-change.lore2")
        )
        // アイテムを光らせる
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        
        // スロット0に配置
        player.inventory.setItem(0, item)
    }
    
    private fun isRoleChangeItem(item: ItemStack): Boolean {
        if (item.type != Material.WRITABLE_BOOK) return false
        val meta = item.itemMeta ?: return false
        // 名前に役割変更のキーワードが含まれているかチェック
        val displayName = meta.displayName ?: return false
        return displayName.contains("役割") || displayName.contains("Role") || displayName.contains("ロール") || displayName.contains("Change")
    }
    
    
    // ======== 名前タグ可視性制御システム ========
    
    // ======== リセットカウントダウンシステム ========
    
    private fun startResetCountdown() {
        // 既存のタスクをキャンセル
        resetCountdownTask?.cancel()
        
        val totalSeconds = 300 // 5分 = 300秒
        var remainingSeconds = totalSeconds
        
        resetCountdownTask = object : BukkitRunnable() {
            override fun run() {
                if (remainingSeconds <= 0) {
                    resetGame()
                    cancel()
                    return
                }
                
                // 全プレイヤーにボスバーを表示
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                val timeString = String.format("%d:%02d", minutes, seconds)
                
                Bukkit.getOnlinePlayers().forEach { player ->
                    val title = messageManager.getMessage(player, "ui.bossbar.reset-countdown", "time" to timeString)
                    val progress = remainingSeconds.toDouble() / totalSeconds.toDouble()
                    
                    plugin.getUIManager().showResetCountdownBossBar(player, title, progress)
                }
                
                // 残り時間が少ない場合は警告
                when (remainingSeconds) {
                    60 -> Bukkit.broadcastMessage(messageManager.getMessage("game.reset-warning-1min"))
                    30 -> Bukkit.broadcastMessage(messageManager.getMessage("game.reset-warning-30sec"))
                    10, 5, 4, 3, 2, 1 -> {
                        Bukkit.broadcastMessage(messageManager.getMessage("game.reset-countdown", "seconds" to remainingSeconds))
                        // サウンド再生
                        Bukkit.getOnlinePlayers().forEach { player ->
                            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)
                        }
                    }
                }
                
                remainingSeconds--
            }
        }
        
        resetCountdownTask?.runTaskTimer(plugin, 0L, 20L) // 1秒ごとに実行
    }
    
    /**
     * 管理者コマンドによる強制リセット
     */
    fun forceReset() {
        // カウントダウンタスクをキャンセル
        resetCountdownTask?.cancel()
        resetCountdownTask = null
        
        // ボスバーをクリア
        Bukkit.getOnlinePlayers().forEach { player ->
            plugin.getUIManager().removeResetCountdownBossBar(player)
        }
        
        // 即座にリセット
        resetGame()
        
        Bukkit.broadcastMessage(messageManager.getMessage("game.force-reset"))
    }
    
    fun setRunnerNameTagVisibility(runner: Player, visible: Boolean) {
        // UIManagerのスコアボードシステムを使用して制御
        val scoreboard = runner.scoreboard
        
        // 一時的なチームを作成または取得
        val teamName = "runner_visibility_${runner.name}"
        var team = scoreboard.getTeam(teamName)
        
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName)
        }
        
        // プレイヤーをチームに追加
        team.addEntry(runner.name)
        
        // 可視性を設定
        if (visible) {
            // スプリント中：全員に表示
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
        } else {
            // 通常時：味方（他のランナー）のみに表示
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OWN_TEAM)
        }
        
        // 全プレイヤーのスコアボードを更新
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer != runner) {
                val viewerScoreboard = viewer.scoreboard
                var viewerTeam = viewerScoreboard.getTeam(teamName)
                
                if (viewerTeam == null) {
                    viewerTeam = viewerScoreboard.registerNewTeam(teamName)
                }
                
                viewerTeam.addEntry(runner.name)
                viewerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, 
                    if (visible) Team.OptionStatus.ALWAYS else Team.OptionStatus.FOR_OWN_TEAM)
            }
        }
    }
}
