package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.random.Random

class GameManager(private val plugin: Main, val configManager: ConfigManager) {
    private var gameState = GameState.WAITING
    private val players = mutableMapOf<UUID, ManhuntPlayer>()
    private val fixedHunters = mutableSetOf<UUID>()
    private var minPlayers = configManager.getMinPlayers()
    private var proximityTask: BukkitRunnable? = null
    
    // ネットワークエラーで退出したプレイヤーの情報を保持
    private val disconnectedPlayers = mutableMapOf<UUID, PlayerRole>()
    
    // ゲーム開始前のゲームモードを保存
    private val originalGameModes = mutableMapOf<UUID, GameMode>()
    
    // パフォーマンス最適化用キャッシュ
    private var cachedHunters: List<Player>? = null
    private var cachedRunners: List<Player>? = null
    private var cacheExpiry = 0L
    private val CACHE_DURATION = 500L // 0.5秒キャッシュ
    
    // リスポン管理
    private val deadRunners = mutableMapOf<UUID, Long>() // プレイヤーID -> 死亡時刻
    private val respawnTasks = mutableMapOf<UUID, BukkitRunnable>() // プレイヤーID -> リスポンタスク
    private val countdownTasks = mutableMapOf<UUID, BukkitRunnable>() // プレイヤーID -> カウントダウンタスク
    
    // 近接警告のクールダウン管理
    private val proximityWarningCooldowns = mutableMapOf<UUID, Long>() // プレイヤーID -> 最後の警告時刻
    
    // ゲーム開始カウントダウン管理
    private var countdownTask: BukkitRunnable? = null
    
    fun getGameState(): GameState = gameState
    
    fun addPlayer(player: Player, role: PlayerRole) {
        players[player.uniqueId] = ManhuntPlayer(player, role)
        invalidateCache()
        checkStartConditions()
        
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
        
        if (gameState == GameState.RUNNING && wasInGame) {
            // ゲーム進行中にプレイヤーが退出した場合
            if (isIntentionalLeave) {
                // 意図的な退出の場合はSpectatorにする
                setPlayerRole(player, PlayerRole.SPECTATOR)
                player.sendMessage("§7ゲームから退出したため、観戦者になりました。")
                Bukkit.broadcastMessage("§e${player.name}がゲームから退出し、観戦者になりました。")
            } else {
                // 切断の場合は元の役割を保存（ネットワークエラーの可能性）
                playerRole?.let { role ->
                    disconnectedPlayers[player.uniqueId] = role
                    players.remove(player.uniqueId)
                    fixedHunters.remove(player.uniqueId)
                    Bukkit.broadcastMessage("§e${player.name}がサーバーから切断しました。")
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
                PlayerRole.RUNNER -> "逃げる人"
                PlayerRole.HUNTER -> "追う人"
                PlayerRole.SPECTATOR -> "観戦者"
            }
            
            player.sendMessage("§aネットワークエラーから復帰しました！役割: $roleText")
            Bukkit.broadcastMessage("§e${player.name}がネットワークエラーから復帰しました！")
            return true
        }
        
        return false
    }
    
    fun setPlayerRole(player: Player, role: PlayerRole) {
        // プレイヤーがゲームに参加していない場合は自動的に参加させる
        if (!players.containsKey(player.uniqueId)) {
            players[player.uniqueId] = ManhuntPlayer(player, role)
        } else {
            players[player.uniqueId]?.role = role
        }
        
        if (role == PlayerRole.HUNTER) {
            fixedHunters.add(player.uniqueId)
        } else {
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
    
    fun getPlayerRole(player: Player): PlayerRole? {
        return players[player.uniqueId]?.role
    }
    
    fun getAllRunners(): List<Player> {
        val currentTime = System.currentTimeMillis()
        if (cachedRunners == null || currentTime > cacheExpiry) {
            cachedRunners = players.values.filter { it.role == PlayerRole.RUNNER }.map { it.player }
            cacheExpiry = currentTime + CACHE_DURATION
        }
        return cachedRunners!!
    }
    
    fun getAllHunters(): List<Player> {
        val currentTime = System.currentTimeMillis()
        if (cachedHunters == null || currentTime > cacheExpiry) {
            cachedHunters = players.values.filter { it.role == PlayerRole.HUNTER }.map { it.player }
            cacheExpiry = currentTime + CACHE_DURATION
        }
        return cachedHunters!!
    }
    
    fun getAllSpectators(): List<Player> {
        return players.values.filter { it.role == PlayerRole.SPECTATOR }.map { it.player }
    }
    
    private fun invalidateCache() {
        cachedHunters = null
        cachedRunners = null
        cacheExpiry = 0L
    }
    
    private fun checkStartConditions() {
        plugin.logger.info("開始条件チェック: ゲーム状態=${gameState}, プレイヤー数=${players.size}, 最小人数=${minPlayers}")
        
        if (gameState == GameState.WAITING) {
            val hunters = getAllHunters()
            val runners = getAllRunners()
            val activePlayerCount = hunters.size + runners.size // 観戦者を除外
            
            plugin.logger.info("詳細チェック: ハンター数=${hunters.size}, ランナー数=${runners.size}, アクティブプレイヤー数=${activePlayerCount}")
            plugin.logger.info("ハンター: ${hunters.map { it.name }}")
            plugin.logger.info("ランナー: ${runners.map { it.name }}")
            
            if (activePlayerCount >= minPlayers && hunters.isNotEmpty() && runners.isNotEmpty()) {
                plugin.logger.info("開始条件満了！ゲームを開始します。")
                startGame()
            } else {
                if (activePlayerCount < minPlayers) {
                    plugin.logger.info("開始条件未満足: アクティブプレイヤー数不足（${activePlayerCount}/${minPlayers}）")
                } else {
                    plugin.logger.info("開始条件未満足: ハンター(${hunters.size})またはランナー(${runners.size})が不足")
                }
            }
        } else {
            plugin.logger.info("開始条件未満足: ゲーム状態=${gameState}がWAITINGではない")
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
            unassigned.first().player.sendMessage("§cハンターが不足のため、自動的にハンターに割り当てられました！")
        }
        
        // ランナーが0人の場合、残りを割り当て
        if (runners.isEmpty() && unassigned.size > 1) {
            unassigned.drop(1).forEach { 
                it.role = PlayerRole.RUNNER
                it.player.sendMessage("§aランナーが不足のため、自動的にランナーに割り当てられました！")
            }
        }
        
        // 最低人数チェック
        val finalHunters = getAllHunters()
        val finalRunners = getAllRunners()
        if (finalHunters.isEmpty() || finalRunners.isEmpty()) {
            gameState = GameState.WAITING
            Bukkit.broadcastMessage("§cハンターとランナーが最低1人ずつ必要です。ゲーム開始を中止しました。")
            return
        }
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
                                chunks <= configManager.getProximityLevel1() -> configManager.getProximityWarningLevel1()
                                chunks <= configManager.getProximityLevel2() -> configManager.getProximityWarningLevel2()
                                chunks <= configManager.getProximityLevel3() -> configManager.getProximityWarningLevel3()
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
                endGame("§c🏆 ハンターの勝利！§f逃げる人を全員倒しました！")
            }
            // 全ハンターが死亡またはゲームから退出した場合
            aliveHunters.isEmpty() -> {
                endGame("§a🏆 ランナーの勝利！§f追う人が全員いなくなりました！")
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
                    endGame("§c追う人の勝利！逃げる人が全員退出しました！")
                }
            }
            PlayerRole.HUNTER -> {
                if (aliveHunters.isEmpty()) {
                    endGame("§a逃げる人の勝利！追う人が全員退出しました！")
                }
            }
            else -> {
                // Spectatorの退出は勝利条件に影響しない
            }
        }
    }
    
    fun onEnderDragonDeath(killer: Player?) {
        if (gameState == GameState.RUNNING && killer != null && getPlayerRole(killer) == PlayerRole.RUNNER) {
            endGame("§a逃げる人の勝利！エンダードラゴンを倒しました！")
        }
    }
    
    private fun endGame(message: String) {
        gameState = GameState.ENDED
        
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
        
        Bukkit.broadcastMessage("§6[Manhunt] ゲーム終了！")
        Bukkit.broadcastMessage(message)
        
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
        
        Bukkit.broadcastMessage(configManager.getGameResetMessage())
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
            plugin.logger.severe("ワールドが見つかりません！")
            return
        }
        
        try {
            // ハンターとランナーの転送地点を生成
            val hunterSpawn = generateRandomSpawnLocation(world)
            val runnerSpawn = generateRandomSpawnLocation(world, minDistanceFromOther = 1000.0)
            
            // ハンターを転送してサバイバルモードに設定
            hunters.forEach { hunter ->
                try {
                    hunter.teleport(hunterSpawn)
                    hunter.gameMode = GameMode.SURVIVAL
                    hunter.sendMessage("§c[ハンター] 逃げる人を追いかけろ！")
                    plugin.logger.info("ハンター ${hunter.name} を ${hunterSpawn.blockX}, ${hunterSpawn.blockY}, ${hunterSpawn.blockZ} に転送")
                } catch (e: Exception) {
                    plugin.logger.warning("ハンター ${hunter.name} の転送でエラー: ${e.message}")
                }
            }
            
            // ランナーを転送してサバイバルモードに設定
            runners.forEach { runner ->
                try {
                    runner.teleport(runnerSpawn)
                    runner.gameMode = GameMode.SURVIVAL
                    runner.sendMessage("§a[ランナー] エンダードラゴンを倒せ！")
                    plugin.logger.info("ランナー ${runner.name} を ${runnerSpawn.blockX}, ${runnerSpawn.blockY}, ${runnerSpawn.blockZ} に転送")
                } catch (e: Exception) {
                    plugin.logger.warning("ランナー ${runner.name} の転送でエラー: ${e.message}")
                }
            }
            
            // 観戦者はスペクテーターモードを継続
            spectators.forEach { spectator ->
                try {
                    spectator.gameMode = GameMode.SPECTATOR
                    spectator.sendMessage("§7[観戦者] ゲームを観戦してください。")
                } catch (e: Exception) {
                    plugin.logger.warning("観戦者 ${spectator.name} のモード設定でエラー: ${e.message}")
                }
            }
            
            Bukkit.broadcastMessage("§6プレイヤーが各開始地点に転送されました！")
            
        } catch (e: Exception) {
            plugin.logger.severe("プレイヤー転送でエラーが発生: ${e.message}")
            // エラーの場合は全員をスペクテーターモードに設定
            (hunters + runners + spectators).forEach { player ->
                try {
                    player.gameMode = GameMode.SPECTATOR
                } catch (ex: Exception) {
                    plugin.logger.warning("緊急モード設定でエラー: ${ex.message}")
                }
            }
        }
    }
    
    private fun generateRandomSpawnLocation(world: World, minDistanceFromOther: Double = 0.0): Location {
        val maxAttempts = 50
        var attempts = 0
        
        while (attempts < maxAttempts) {
            attempts++
            
            // ランダムな座標を生成（-2000 ~ +2000の範囲）
            val x = Random.nextDouble(-2000.0, 2000.0)
            val z = Random.nextDouble(-2000.0, 2000.0)
            
            // 安全な高度を見つける
            val safeY = findSafeY(world, x.toInt(), z.toInt())
            
            if (safeY > 0) {
                val location = Location(world, x, safeY.toDouble(), z)
                
                // 他の転送地点との距離をチェック（必要な場合）
                if (minDistanceFromOther <= 0.0 || isLocationSafeDistance(location, minDistanceFromOther)) {
                    plugin.logger.info("安全な転送地点を生成: ${x.toInt()}, $safeY, ${z.toInt()} (試行回数: $attempts)")
                    return location
                }
            }
        }
        
        // フォールバック: ワールドスポーン地点
        plugin.logger.warning("安全な転送地点の生成に失敗。ワールドスポーンを使用します。")
        return world.spawnLocation
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
    
    private fun isLocationSafeDistance(location: Location, minDistance: Double): Boolean {
        // 現在は簡単な実装（複数の転送地点を記録して比較する場合に使用）
        return true
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
                    player.sendMessage("§c[ハンター] リスポンしました！追跡を続けてください。")
                    plugin.logger.info("ハンター ${player.name} が即座リスポンしました")
                }
            }, 1L) // 1tick後にリスポン
        } catch (e: Exception) {
            plugin.logger.warning("ハンター ${player.name} のリスポン処理でエラー: ${e.message}")
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
        player.sendMessage("§c[ランナー] 死亡しました。${respawnTime}秒後にリスポンします...")
        Bukkit.broadcastMessage("§e${player.name} (ランナー) が死亡しました。${respawnTime}秒後にリスポンします。")
        
        // リスポン待ち中はスペクテーターモードに変更
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            try {
                if (player.isOnline && gameState == GameState.RUNNING && deadRunners.containsKey(player.uniqueId)) {
                    player.gameMode = GameMode.SPECTATOR
                    player.sendMessage("§7[リスポン待ち] スペクテーターモードでゲームを観戦できます。")
                }
            } catch (e: Exception) {
                plugin.logger.warning("ランナー ${player.name} のスペクテーターモード設定でエラー: ${e.message}")
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
                        
                        player.sendMessage("§a[ランナー] リスポンしました！エンダードラゴンを倒してください。")
                        Bukkit.broadcastMessage("§e${player.name} (ランナー) がリスポンしました。")
                        
                        // UIManager経由でタイトルクリア
                        try {
                            plugin.getUIManager().showTitle(player, "§a✓ リスポン完了", "§fエンダードラゴンを倒そう！", 10, 30, 10)
                        } catch (e: Exception) {
                            plugin.logger.warning("UI表示でエラー: ${e.message}")
                        }
                        
                        plugin.logger.info("ランナー ${player.name} が${respawnTime}秒後にリスポンしました")
                        
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
                    plugin.logger.warning("ランナー ${player.name} のリスポン処理でエラー: ${e.message}")
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
                        val title = "§c💀 死亡中"
                        val subtitle = "§f${remainingTime}秒後にリスポン"
                        
                        try {
                            plugin.getUIManager().showTitle(player, title, subtitle, 0, 25, 0)
                        } catch (e: Exception) {
                            // フォールバック: チャットメッセージ
                            player.sendMessage("§c[リスポン] あと ${remainingTime}秒...")
                        }
                        
                        // 最後の3秒は音とメッセージで強調
                        if (remainingTime <= 3) {
                            try {
                                player.playSound(player.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (3 - remainingTime) * 0.2f)
                            } catch (e: Exception) {
                                // 音再生エラーは無視
                            }
                            player.sendMessage("§e§l${remainingTime}...")
                        }
                        
                        remainingTime--
                    } else {
                        // カウントダウン終了
                        cancel()
                        countdownTasks.remove(player.uniqueId)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("カウントダウン表示でエラー: ${e.message}")
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
        Bukkit.broadcastMessage("§6[Manhunt] ゲーム開始まで ${countdownSeconds}秒...")
        
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
                        val title = "§6🎮 ゲーム開始"
                        val subtitle = "§f${remainingTime}秒後に開始"
                        
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
                            Bukkit.broadcastMessage("§e§l${remainingTime}...")
                        }
                        
                        remainingTime--
                    } else {
                        // カウントダウン終了 - 実際のゲーム開始
                        cancel()
                        actuallyStartGame()
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("カウントダウンでエラー: ${e.message}")
                    cancel()
                    actuallyStartGame() // エラー時も強制開始
                }
            }
        }
        
        countdownTask?.runTaskTimer(plugin, 20L, 20L) // 1秒後から1秒間隔で実行
    }
    
    private fun actuallyStartGame() {
        // ハンターとランナーをランダムな場所に転送し、サバイバルモードに設定
        teleportPlayersToStartPositions()
        
        gameState = GameState.RUNNING
        
        // UIにゲーム実行状態を通知
        try {
            plugin.getUIManager().showGameStateChange(GameState.RUNNING)
        } catch (e: Exception) {
            plugin.logger.warning("UI通知でエラー: ${e.message}")
        }
        
        // Broadcast game start
        Bukkit.broadcastMessage(configManager.getGameStartMessage())
        Bukkit.broadcastMessage("§a逃げる人: エンダードラゴンを倒せ！")
        Bukkit.broadcastMessage("§c追う人: 逃げる人を全員倒せ！")
        Bukkit.broadcastMessage("§7観戦者はスペクテーターモードで観戦します。")
        
        // Start proximity checking
        startProximityChecking()
        
        // Start compass tracking
        plugin.getCompassTracker().startTracking()
        
        // 開始完了タイトル
        Bukkit.getOnlinePlayers().forEach { player ->
            try {
                plugin.getUIManager().showTitle(player, "§a🚀 ゲーム開始！", "§f頑張って！", 10, 30, 10)
            } catch (e: Exception) {
                // タイトル表示エラーは無視
            }
        }
    }
    
}