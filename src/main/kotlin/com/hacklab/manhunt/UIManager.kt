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
    
    private lateinit var partyManager: PartyManager
    
    fun setPartyManager(partyManager: PartyManager) {
        this.partyManager = partyManager
    }
    
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
        
        // ゲーム中の詳細情報
        if (gameState == GameState.RUNNING) {
            addScoreboardLine("§r   ", line--) // 空行
        } else {
            // 待機中はプレイヤー数表示
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
        
        // 全プレイヤーにスコアボード適用（個別にパーティー情報を追加）
        onlinePlayers.forEach { player ->
            updatePlayerSpecificScoreboard(player, line)
        }
    }
    
    private fun updatePlayerSpecificScoreboard(player: Player, baseLine: Int) {
        // プレイヤー専用のスコアボードを作成
        val playerScoreboard = Bukkit.getScoreboardManager()?.newScoreboard ?: return
        val playerObjective = playerScoreboard.registerNewObjective("manhunt", "dummy", "§6🏃 MANHUNT")
        playerObjective.displaySlot = DisplaySlot.SIDEBAR
        
        var line = baseLine
        
        val gameState = gameManager.getGameState()
        val role = gameManager.getPlayerRole(player)
        
        // ゲーム中でパーティーに参加している場合は、パーティー情報を表示
        if (gameState == GameState.RUNNING && ::partyManager.isInitialized) {
            val party = partyManager.getPlayerParty(player.name)
            if (party != null && role != PlayerRole.SPECTATOR) {
                val otherMembers = party.getOtherMembers(player.name)
                
                if (otherMembers.isNotEmpty()) {
                    // パーティーヘッダー
                    val roleColor = when (role) {
                        PlayerRole.HUNTER -> "§c"
                        PlayerRole.RUNNER -> "§a"
                        else -> "§7"
                    }
                    addPlayerScoreboardLine(playerObjective, "${roleColor}🤝 パーティーメンバー", line--)
                    addPlayerScoreboardLine(playerObjective, "§r", line--) // 空行
                    
                    // メンバー情報表示（最大1人、パーティーサイズ2のため）
                    otherMembers.take(1).forEach { memberName ->
                        val member = plugin.server.getPlayer(memberName)
                        if (member?.isOnline == true && member.world == player.world) {
                            // 座標差分計算
                            val deltaX = member.location.blockX - player.location.blockX
                            val deltaY = member.location.blockY - player.location.blockY
                            val deltaZ = member.location.blockZ - player.location.blockZ
                            
                            addPlayerScoreboardLine(playerObjective, "§f${memberName}:", line--)
                            addPlayerScoreboardLine(playerObjective, "§7X:${deltaX} Y:${deltaY}", line--)
                            addPlayerScoreboardLine(playerObjective, "§7Z:${deltaZ}", line--)
                        } else {
                            addPlayerScoreboardLine(playerObjective, "§f${memberName}:", line--)
                            addPlayerScoreboardLine(playerObjective, "§cオフライン", line--)
                        }
                    }
                    
                    addPlayerScoreboardLine(playerObjective, "§r ", line--) // 空行
                }
            }
        }
        
        // 共通のスコアボード内容をコピー
        objective?.let { originalObjective ->
            originalObjective.scoreboard?.getEntries()?.forEach { entry ->
                val score = originalObjective.getScore(entry).score
                if (score <= line) { // パーティー情報より下に表示
                    addPlayerScoreboardLine(playerObjective, entry, score)
                }
            }
        }
        
        // プレイヤーにスコアボードを適用
        player.scoreboard = playerScoreboard
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
    
    // ======== パーティー情報表示 ========
    
    private fun addPartyInfoToScoreboard(player: Player, startLine: Int) {
        if (!::partyManager.isInitialized) return
        
        val party = partyManager.getPlayerParty(player.name) ?: return
        val otherMembers = party.getOtherMembers(player.name)
        
        if (otherMembers.isEmpty()) return
        
        var line = startLine
        
        // パーティーヘッダー
        val roleColor = when (gameManager.getPlayerRole(player)) {
            PlayerRole.HUNTER -> "§c"
            PlayerRole.RUNNER -> "§a"
            else -> "§7"
        }
        addScoreboardLine("${roleColor}[${player.name}のパーティー]", line--)
        
        // メンバー情報表示（最大2人）
        otherMembers.take(1).forEach { memberName ->
            val member = plugin.server.getPlayer(memberName)
            if (member?.isOnline == true) {
                // 座標差分計算
                val deltaX = member.location.blockX - player.location.blockX
                val deltaY = member.location.blockY - player.location.blockY
                val deltaZ = member.location.blockZ - player.location.blockZ
                
                addScoreboardLine("§f${memberName}:", line--)
                addScoreboardLine("§7 X:${deltaX} Y:${deltaY} Z:${deltaZ}", line--)
            } else {
                addScoreboardLine("§f${memberName}: §cオフライン", line--)
            }
        }
        
        addScoreboardLine("§r", line--) // 空行
    }
    
    private fun getPartyInfoLines(player: Player): Int {
        if (!::partyManager.isInitialized) return 0
        
        val party = partyManager.getPlayerParty(player.name) ?: return 0
        val otherMembers = party.getOtherMembers(player.name)
        
        if (otherMembers.isEmpty()) return 0
        
        // ヘッダー(1行) + メンバー情報(2行 per member) + 空行(1行)
        return 1 + (otherMembers.take(1).size * 2) + 1
    }
    
    // ======== ActionBar用パーティー情報（削除予定） ========
    
    private fun getPartyActionBarInfo(player: Player): String {
        // PartyManagerが初期化されていない場合は空文字を返す
        if (!::partyManager.isInitialized) {
            return ""
        }
        
        val party = partyManager.getPlayerParty(player.name) ?: return ""
        val otherMembers = party.getOtherMembers(player.name)
        
        if (otherMembers.isEmpty()) {
            return "" // パーティーに自分しかいない場合は表示しない
        }
        
        val partyInfo = StringBuilder("🤝 ")
        val maxDisplay = 1 // デバッグ用: 最大1人まで表示
        
        val onlineMembers = otherMembers.take(maxDisplay).mapNotNull { memberName ->
            val member = plugin.server.getPlayer(memberName)
            if (member?.isOnline == true) {
                val distance = try {
                    val actualDistance = player.location.distance(member.location)
                    if (actualDistance < 0) -1 else actualDistance.toInt()
                } catch (e: Exception) {
                    -1
                }
                
                val direction = getDirectionSymbol(player, member)
                if (distance >= 0) {
                    "${memberName}: ${distance}m${direction}"
                } else {
                    "${memberName}: ?m?"
                }
            } else {
                null
            }
        }
        
        if (onlineMembers.isEmpty()) {
            return "" // オンラインメンバーがいない場合は表示しない
        }
        
        partyInfo.append(onlineMembers.joinToString(" | "))
        
        // 表示しきれないメンバーがいる場合（オンライン人数ベース）
        val onlineMembersInParty = otherMembers.count { memberName ->
            plugin.server.getPlayer(memberName)?.isOnline == true
        }
        val remainingCount = onlineMembersInParty - onlineMembers.size
        if (remainingCount > 0) {
            partyInfo.append(" +${remainingCount}人")
        }
        
        return partyInfo.toString()
    }
    
    private fun getDirectionSymbol(from: Player, to: Player): String {
        return try {
            if (from.world != to.world) return "?"
            
            val fromLoc = from.location
            val toLoc = to.location
            
            val deltaX = toLoc.x - fromLoc.x
            val deltaZ = toLoc.z - fromLoc.z
            
            val angle = Math.atan2(deltaZ, deltaX) * 180 / Math.PI
            val normalizedAngle = (angle + 360) % 360
            
            when (normalizedAngle.toInt()) {
                in 0..22, in 338..360 -> "→" // 東
                in 23..67 -> "↘" // 南東
                in 68..112 -> "↓" // 南
                in 113..157 -> "↙" // 南西
                in 158..202 -> "←" // 西
                in 203..247 -> "↖" // 北西
                in 248..292 -> "↑" // 北
                in 293..337 -> "↗" // 北東
                else -> "?"
            }
        } catch (e: Exception) {
            "?"
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