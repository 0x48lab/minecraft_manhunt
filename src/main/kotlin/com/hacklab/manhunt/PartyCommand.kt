package com.hacklab.manhunt

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * パーティーコマンドを処理するクラス
 */
class PartyCommand(
    private val plugin: Main,
    private val partyManager: PartyManager,
    private val gameManager: GameManager
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみが実行できます。")
            return true
        }
        
        if (args.isEmpty()) {
            sendHelpMessage(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "create" -> handleCreateCommand(sender)
            "invite" -> handleInviteCommand(sender, args)
            "accept" -> handleAcceptCommand(sender)
            "decline" -> handleDeclineCommand(sender)
            "leave" -> handleLeaveCommand(sender)
            "kick" -> handleKickCommand(sender, args)
            "list", "info" -> handleListCommand(sender)
            "gui" -> handleGuiCommand(sender)
            "debug" -> handleDebugCommand(sender, args)
            "help" -> sendHelpMessage(sender)
            else -> {
                sender.sendMessage("§c不明なサブコマンドです。 /manhunt party help を実行してください。")
            }
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        
        return when (args.size) {
            1 -> {
                val subCommands = listOf("create", "invite", "accept", "decline", "leave", "kick", "list", "gui", "help")
                subCommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "invite" -> {
                        // オンラインプレイヤーのリストを返す（パーティー未参加者のみ）
                        plugin.server.onlinePlayers
                            .filter { it != sender && partyManager.getPlayerParty(it.name) == null }
                            .map { it.name }
                            .filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                    "kick" -> {
                        // 自分のパーティーメンバーのリストを返す（リーダー以外）
                        val party = partyManager.getPlayerParty(sender.name)
                        if (party != null && party.isLeader(sender.name)) {
                            party.getOtherMembers(sender.name)
                                .filter { it.startsWith(args[1], ignoreCase = true) }
                        } else {
                            emptyList()
                        }
                    }
                    "debug" -> {
                        listOf("actionbar", "fake", "distance", "direction").filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
    
    private fun handleCreateCommand(player: Player) {
        // 既にパーティーに参加している場合
        if (partyManager.getPlayerParty(player.name) != null) {
            player.sendMessage("§c既にパーティーに参加しています。")
            return
        }
        
        val party = partyManager.createParty(player)
        if (party != null) {
            val roleText = party.role?.let { " (${it.name})" } ?: ""
            player.sendMessage("§a[パーティー] パーティー$roleText を作成しました！")
            player.sendMessage("§e/manhunt party invite <プレイヤー名> §7- メンバーを招待")
            player.sendMessage("§e/manhunt party gui §7- GUI管理画面を開く")
        } else {
            player.sendMessage("§cパーティーの作成に失敗しました。")
        }
    }
    
    private fun handleInviteCommand(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§c使用方法: /manhunt party invite <プレイヤー名>")
            return
        }
        
        val targetName = args[1]
        val party = partyManager.getPlayerParty(player.name)
        
        if (party == null) {
            player.sendMessage("§cパーティーに参加していません。")
            return
        }
        
        if (!party.isLeader(player.name)) {
            player.sendMessage("§cリーダーのみが招待できます。")
            return
        }
        
        if (party.isFull()) {
            player.sendMessage("§cパーティーが満員です。(${Party.MAX_PARTY_SIZE}人)")
            return
        }
        
        val target = plugin.server.getPlayer(targetName)
        if (target == null) {
            player.sendMessage("§cプレイヤー '${targetName}' が見つかりません。")
            return
        }
        
        if (target == player) {
            player.sendMessage("§c自分自身を招待することはできません。")
            return
        }
        
        // 役割チェック（ゲーム中の場合）
        if (gameManager.getGameState() == GameState.RUNNING) {
            val playerRole = gameManager.getPlayerRole(player)
            val targetRole = gameManager.getPlayerRole(target)
            
            if (playerRole != targetRole) {
                player.sendMessage("§c異なる役割のプレイヤーは招待できません。")
                return
            }
        }
        
        if (partyManager.sendInvite(player, targetName)) {
            // 成功メッセージはPartyManager内で送信される
        } else {
            player.sendMessage("§c招待の送信に失敗しました。")
            player.sendMessage("§7- 対象プレイヤーが既にパーティーに参加している")
            player.sendMessage("§7- 対象プレイヤーが既に招待を受信している")
            player.sendMessage("§7- パーティーが満員")
        }
    }
    
    private fun handleAcceptCommand(player: Player) {
        val invite = partyManager.getPendingInvite(player.name)
        if (invite == null) {
            player.sendMessage("§c受信中の招待がありません。")
            return
        }
        
        if (partyManager.acceptInvite(player)) {
            // 成功メッセージはPartyManager内で送信される
        } else {
            player.sendMessage("§c招待の承諾に失敗しました。")
            player.sendMessage("§7招待が期限切れまたはパーティーが満員の可能性があります。")
        }
    }
    
    private fun handleDeclineCommand(player: Player) {
        val invite = partyManager.getPendingInvite(player.name)
        if (invite == null) {
            player.sendMessage("§c受信中の招待がありません。")
            return
        }
        
        if (partyManager.declineInvite(player)) {
            // 成功メッセージはPartyManager内で送信される
        } else {
            player.sendMessage("§c招待の拒否に失敗しました。")
        }
    }
    
    private fun handleLeaveCommand(player: Player) {
        val party = partyManager.getPlayerParty(player.name)
        if (party == null) {
            player.sendMessage("§cパーティーに参加していません。")
            return
        }
        
        if (party.isLeader(player.name)) {
            // 脱退確認をチェック
            if (hasLeaveConfirmation(player)) {
                // 確認済みの場合は解散実行
                if (partyManager.leaveParty(player)) {
                    // 成功メッセージはPartyManager内で送信される
                } else {
                    player.sendMessage("§cパーティーの解散に失敗しました。")
                }
                return
            } else {
                // 初回実行時は確認メッセージ
                player.sendMessage("§e[確認] リーダーが脱退するとパーティーが解散されます。")
                player.sendMessage("§e本当に脱退しますか？ 30秒以内に再度コマンドを実行してください。")
                
                // 確認用タスクを設定
                setLeaveConfirmation(player)
                return
            }
        }
        
        if (partyManager.leaveParty(player)) {
            // 成功メッセージはPartyManager内で送信される
        } else {
            player.sendMessage("§cパーティーからの脱退に失敗しました。")
        }
    }
    
    private fun handleKickCommand(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§c使用方法: /manhunt party kick <プレイヤー名>")
            return
        }
        
        val targetName = args[1]
        val party = partyManager.getPlayerParty(player.name)
        
        if (party == null) {
            player.sendMessage("§cパーティーに参加していません。")
            return
        }
        
        if (!party.isLeader(player.name)) {
            player.sendMessage("§cリーダーのみがメンバーを除名できます。")
            return
        }
        
        if (!party.isMember(targetName)) {
            player.sendMessage("§c'${targetName}' はパーティーメンバーではありません。")
            return
        }
        
        if (party.isLeader(targetName)) {
            player.sendMessage("§cリーダーは除名できません。")
            return
        }
        
        if (partyManager.kickMember(player, targetName)) {
            // 成功メッセージはPartyManager内で送信される
        } else {
            player.sendMessage("§cメンバーの除名に失敗しました。")
        }
    }
    
    private fun handleListCommand(player: Player) {
        val party = partyManager.getPlayerParty(player.name)
        
        if (party == null) {
            player.sendMessage("§cパーティーに参加していません。")
            
            // 受信中の招待があるかチェック
            val invite = partyManager.getPendingInvite(player.name)
            if (invite != null) {
                val inviterName = invite.inviterName
                player.sendMessage("§6[招待] §f$inviterName §6からの招待があります")
                player.sendMessage("§e/manhunt party accept §7- 承諾")
                player.sendMessage("§e/manhunt party decline §7- 拒否")
            }
            return
        }
        
        // パーティー情報を表示
        player.sendMessage(party.getDisplayInfo())
        
        // オンライン状況を表示
        player.sendMessage("§7═══ オンライン状況 ═══")
        party.members.forEach { memberName ->
            val member = plugin.server.getPlayer(memberName)
            val onlineStatus = if (member?.isOnline == true) "§aオンライン" else "§cオフライン"
            val leaderMark = if (party.isLeader(memberName)) "§6👑 " else "§f  "
            player.sendMessage("$leaderMark§f$memberName §7- $onlineStatus")
        }
        
        if (party.isLeader(player.name)) {
            val remainingSlots = Party.MAX_PARTY_SIZE - party.getMemberCount()
            if (remainingSlots > 0) {
                player.sendMessage("§7あと §e$remainingSlots §7人招待できます")
                player.sendMessage("§e/manhunt party invite <プレイヤー名> §7- 招待")
            }
        }
    }
    
    private fun handleGuiCommand(player: Player) {
        val party = partyManager.getPlayerParty(player.name)
        
        if (party == null) {
            player.sendMessage("§cパーティーに参加していません。")
            player.sendMessage("§e/manhunt party create §7- パーティーを作成")
            return
        }
        
        // GUI開放（PartyGUI実装後）
        player.sendMessage("§eGUI機能は実装予定です。現在は /manhunt party list でパーティー情報を確認できます。")
    }
    
    private fun handleDebugCommand(player: Player, args: Array<out String>) {
        if (!player.hasPermission("manhunt.admin")) {
            player.sendMessage("§cこのコマンドは管理者のみが実行できます。")
            return
        }
        
        if (args.size < 2) {
            player.sendMessage("§6=== パーティーデバッグコマンド ===")
            player.sendMessage("§e/manhunt party debug actionbar §7- ActionBar表示テスト")
            player.sendMessage("§e/manhunt party debug fake §7- 仮想パーティーメンバー作成")
            player.sendMessage("§e/manhunt party debug distance §7- 距離計算テスト")
            player.sendMessage("§e/manhunt party debug direction §7- 方向矢印テスト")
            return
        }
        
        when (args[1].lowercase()) {
            "actionbar" -> debugActionBar(player)
            "fake" -> debugCreateFakeParty(player)
            "distance" -> debugDistanceCalculation(player)
            "direction" -> debugDirectionTest(player)
            else -> {
                player.sendMessage("§c不明なデバッグコマンドです。")
            }
        }
    }
    
    private fun debugActionBar(player: Player) {
        val uiManager = plugin.getUIManager()
        
        player.sendMessage("§a[デバッグ] ActionBar表示テストを開始します...")
        
        // 5秒間、様々なパターンのActionBarを表示
        val patterns = listOf(
            "§c🗡 ハンターモード §8| §f🤝 TestPlayer: 45m↗",
            "§a🏃 ランナーモード §8| §f🤝 TestPlayer: 123m←",
            "§c🗡 ハンターモード §8| §f🤝 TestPlayer: 89m↓ +1人",
            "§a🏃 ランナーモード §8| §f🤝 FarPlayer: 256m→",
            "§c🗡 ハンターモード §8| §f🤝 NearPlayer: 12m↖"
        )
        
        patterns.forEachIndexed { index, pattern ->
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                uiManager.sendActionBar(player, pattern)
                player.sendMessage("§7[${index + 1}/${patterns.size}] $pattern")
            }, (index * 20L)) // 1秒間隔
        }
        
        // 元の表示に戻す
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            player.sendMessage("§a[デバッグ] ActionBarテスト完了")
        }, (patterns.size * 20L))
    }
    
    private fun debugCreateFakeParty(player: Player) {
        // 既存のパーティーから脱退
        partyManager.getPlayerParty(player.name)?.let { 
            partyManager.leaveParty(player)
        }
        
        // 新しいパーティーを作成
        val party = partyManager.createParty(player)
        if (party != null) {
            // 仮想メンバーを追加（実際には存在しない）
            party.addMember("TestPlayer1")
            
            player.sendMessage("§a[デバッグ] 仮想パーティーを作成しました")
            player.sendMessage("§7メンバー: ${party.members.joinToString(", ")}")
            player.sendMessage("§7※ ActionBarでの表示をテストできます")
            player.sendMessage("§e移動して位置表示の更新を確認してください")
        } else {
            player.sendMessage("§c仮想パーティーの作成に失敗しました")
        }
    }
    
    private fun debugDistanceCalculation(player: Player) {
        val testLocations = listOf(
            player.location.clone().add(10.0, 0.0, 0.0) to "東10m",
            player.location.clone().add(0.0, 0.0, 20.0) to "南20m", 
            player.location.clone().add(-15.0, 0.0, 0.0) to "西15m",
            player.location.clone().add(0.0, 0.0, -25.0) to "北25m",
            player.location.clone().add(50.0, 0.0, 50.0) to "南東71m"
        )
        
        player.sendMessage("§a[デバッグ] 距離計算テスト")
        testLocations.forEach { (location, expected) ->
            val distance = player.location.distance(location).toInt()
            player.sendMessage("§7予想: $expected → 計算結果: ${distance}m")
        }
    }
    
    private fun debugDirectionTest(player: Player) {
        val directions = listOf(
            "東" to player.location.clone().add(10.0, 0.0, 0.0),
            "南東" to player.location.clone().add(10.0, 0.0, 10.0),
            "南" to player.location.clone().add(0.0, 0.0, 10.0),
            "南西" to player.location.clone().add(-10.0, 0.0, 10.0),
            "西" to player.location.clone().add(-10.0, 0.0, 0.0),
            "北西" to player.location.clone().add(-10.0, 0.0, -10.0),
            "北" to player.location.clone().add(0.0, 0.0, -10.0),
            "北東" to player.location.clone().add(10.0, 0.0, -10.0)
        )
        
        player.sendMessage("§a[デバッグ] 方向矢印テスト")
        directions.forEach { (expected, location) ->
            val deltaX = location.x - player.location.x
            val deltaZ = location.z - player.location.z
            val angle = Math.atan2(deltaZ, deltaX) * 180 / Math.PI
            val normalizedAngle = (angle + 360) % 360
            
            val arrow = when (normalizedAngle.toInt()) {
                in 0..22, in 338..360 -> "→"
                in 23..67 -> "↘"
                in 68..112 -> "↓"
                in 113..157 -> "↙"
                in 158..202 -> "←"
                in 203..247 -> "↖"
                in 248..292 -> "↑"
                in 293..337 -> "↗"
                else -> "?"
            }
            
            player.sendMessage("§7${expected}: $arrow (角度: ${normalizedAngle.toInt()}°)")
        }
    }
    
    private fun sendHelpMessage(player: Player) {
        player.sendMessage("§6═══ パーティーコマンド ═══")
        player.sendMessage("§e/manhunt party create §7- パーティーを作成")
        player.sendMessage("§e/manhunt party invite <名前> §7- プレイヤーを招待")
        player.sendMessage("§e/manhunt party accept §7- 招待を承諾")
        player.sendMessage("§e/manhunt party decline §7- 招待を拒否")
        player.sendMessage("§e/manhunt party leave §7- パーティーから脱退")
        player.sendMessage("§e/manhunt party kick <名前> §7- メンバーを除名（リーダーのみ）")
        player.sendMessage("§e/manhunt party list §7- パーティー情報を表示")
        player.sendMessage("§e/manhunt party gui §7- GUI管理画面（実装予定）")
        if (player.hasPermission("manhunt.admin")) {
            player.sendMessage("§c/manhunt party debug §7- デバッグ機能（管理者のみ）")
        }
        player.sendMessage("§7最大 §e${Party.MAX_PARTY_SIZE}人 §7まで同じ役割でパーティーを組めます")
        player.sendMessage("§7パーティーメンバーの位置はサイドバーに表示されます")
    }
    
    // 脱退確認システム（簡易版）
    private val leaveConfirmations = mutableMapOf<String, Long>()
    
    private fun setLeaveConfirmation(player: Player) {
        leaveConfirmations[player.name] = System.currentTimeMillis()
        
        // 30秒後に確認をクリア
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            leaveConfirmations.remove(player.name)
        }, 20L * 30)
    }
    
    private fun hasLeaveConfirmation(player: Player): Boolean {
        val confirmTime = leaveConfirmations[player.name] ?: return false
        val timeDiff = System.currentTimeMillis() - confirmTime
        
        if (timeDiff > 30000) { // 30秒経過
            leaveConfirmations.remove(player.name)
            return false
        }
        
        leaveConfirmations.remove(player.name)
        return true
    }
}