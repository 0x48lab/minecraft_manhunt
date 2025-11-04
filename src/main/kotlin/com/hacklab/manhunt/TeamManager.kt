package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

/**
 * Minecraftのスコアボードチームを管理し、プレイヤー名の色を役割に応じて設定する
 *
 * このクラスはMinecraftのネイティブScoreboard Teams APIを使用して、プレイヤー名の色を管理します。
 * ハンター（赤）、ランナー（青）、観戦者（灰色）の3つのチームを作成し、
 * プレイヤーのロール変更時に自動的にチームを切り替えます。
 *
 * ライフサイクル:
 * 1. initialize() - プラグイン有効化時に3つのチームを作成・登録
 * 2. assignTeam() - ゲーム開始時・ロール変更時にプレイヤーをチームに割り当て
 * 3. removeFromAllTeams() - ゲーム終了時・プレイヤー退出時にチームから削除
 * 4. cleanup() - プラグイン無効化時にすべてのチームを登録解除
 *
 * スレッドセーフティ:
 * - このクラスのすべてのメソッドはメインサーバースレッドから呼び出す必要があります
 * - Bukkit APIはメインスレッド以外からの呼び出しをサポートしていません
 * - 非同期処理から呼び出す場合は、Bukkit.getScheduler().runTask()を使用してください
 *
 * 互換性:
 * - Minecraft 1.21以降で動作確認済み
 * - Scoreboard Teams APIはMinecraft 1.5以降で利用可能
 */
class TeamManager(private val plugin: Main) {
    private val scoreboard: Scoreboard = Bukkit.getScoreboardManager()!!.mainScoreboard
    private val teams: MutableMap<PlayerRole, Team> = mutableMapOf()

    companion object {
        private const val HUNTER_TEAM_NAME = "manhunt_hunters"
        private const val RUNNER_TEAM_NAME = "manhunt_runners"
        private const val SPECTATOR_TEAM_NAME = "manhunt_spectators"
    }

    /**
     * 3つのスコアボードチーム（ハンター、ランナー、観戦者）を初期化する
     * 既存のチームがある場合は再利用し、ない場合は新規作成する
     */
    fun initialize() {
        // ハンターチームの初期化
        val hunterTeam = scoreboard.getTeam(HUNTER_TEAM_NAME) ?: scoreboard.registerNewTeam(HUNTER_TEAM_NAME)
        hunterTeam.color = ChatColor.RED
        hunterTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
        teams[PlayerRole.HUNTER] = hunterTeam

        // ランナーチームの初期化
        val runnerTeam = scoreboard.getTeam(RUNNER_TEAM_NAME) ?: scoreboard.registerNewTeam(RUNNER_TEAM_NAME)
        runnerTeam.color = ChatColor.BLUE
        runnerTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
        teams[PlayerRole.RUNNER] = runnerTeam

        // 観戦者チームの初期化
        val spectatorTeam = scoreboard.getTeam(SPECTATOR_TEAM_NAME) ?: scoreboard.registerNewTeam(SPECTATOR_TEAM_NAME)
        spectatorTeam.color = ChatColor.GRAY
        spectatorTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS)
        teams[PlayerRole.SPECTATOR] = spectatorTeam

        plugin.logger.info("TeamManager initialized with 3 teams (Hunters: RED, Runners: BLUE, Spectators: GRAY)")
    }

    /**
     * プレイヤーを指定された役割のチームに割り当てる
     * 既存のチームに所属している場合は、そのチームから削除してから新しいチームに追加する
     *
     * @param player 割り当てるプレイヤー
     * @param role プレイヤーの役割
     */
    fun assignTeam(player: Player, role: PlayerRole) {
        // まず既存のすべてのmanhuntチームから削除
        removeFromAllTeams(player)

        // 新しいチームに追加
        val team = teams[role]
        if (team != null) {
            team.addEntry(player.name)
        } else {
            plugin.logger.warning("Team for role $role not found. Did you call initialize()?")
        }
    }

    /**
     * プレイヤーをすべてのmanhuntチームから削除する
     * プレイヤーが切断した際やゲーム終了時に呼び出される
     *
     * @param player 削除するプレイヤー
     */
    fun removeFromAllTeams(player: Player) {
        teams.values.forEach { team ->
            team.removeEntry(player.name)
        }
    }

    /**
     * すべてのmanhuntチームを登録解除する
     * プラグイン無効化時に呼び出される
     */
    fun cleanup() {
        teams.values.forEach { team ->
            team.unregister()
        }
        teams.clear()
        plugin.logger.info("TeamManager cleaned up - all teams unregistered")
    }

    /**
     * 指定された役割のチームを取得する
     *
     * @param role 役割
     * @return 対応するチーム、存在しない場合はnull
     */
    fun getTeam(role: PlayerRole): Team? {
        // TODO: T009で実装
        return teams[role]
    }
}
