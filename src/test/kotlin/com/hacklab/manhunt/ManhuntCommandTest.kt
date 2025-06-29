package com.hacklab.manhunt

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.mockito.MockedStatic
import java.util.*

/**
 * ManhuntCommandの単体テスト
 */
class ManhuntCommandTest {
    
    private lateinit var mockGameManager: GameManager
    private lateinit var mockCompassTracker: CompassTracker
    private lateinit var manhuntCommand: ManhuntCommand
    private lateinit var mockPlayer: Player
    private lateinit var mockSender: CommandSender
    private lateinit var mockCommand: Command
    
    @BeforeEach
    fun setUp() {
        mockGameManager = mock(GameManager::class.java)
        mockCompassTracker = mock(CompassTracker::class.java)
        manhuntCommand = ManhuntCommand(mockGameManager, mockCompassTracker)
        
        mockPlayer = mock(Player::class.java)
        mockSender = mock(CommandSender::class.java)
        mockCommand = mock(Command::class.java)
        
        // プレイヤーモックの基本設定
        `when`(mockPlayer.uniqueId).thenReturn(UUID.randomUUID())
        `when`(mockPlayer.name).thenReturn("TestPlayer")
        `when`(mockPlayer.hasPermission("manhunt.admin")).thenReturn(false)
    }
    
    @Test
    fun `空の引数でヘルプが表示されることを確認`() {
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf())
        
        assertTrue(result)
        verify(mockSender, atLeastOnce()).sendMessage(contains("=== Manhunt コマンド ==="))
    }
    
    @Test
    fun `join コマンドがプレイヤー以外で実行された場合にエラーメッセージが表示されることを確認`() {
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("join"))
        
        assertTrue(result)
        verify(mockSender).sendMessage("§cプレイヤーのみが実行できるコマンドです。")
    }
    
    @Test
    fun `join コマンドでプレイヤーが正しく追加されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("join", "hunter"))
        
        assertTrue(result)
        verify(mockGameManager).addPlayer(mockPlayer, PlayerRole.HUNTER)
        verify(mockPlayer).sendMessage("§a追う人として参加しました！")
    }
    
    @Test
    fun `join コマンドでゲーム進行中に新規参加が拒否されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.RUNNING)
        `when`(mockGameManager.handleRejoin(mockPlayer)).thenReturn(false)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("join"))
        
        assertTrue(result)
        verify(mockPlayer).sendMessage("§cゲーム進行中は新規参加できません。")
    }
    
    @Test
    fun `join コマンドで再参加が成功することを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.RUNNING)
        `when`(mockGameManager.handleRejoin(mockPlayer)).thenReturn(true)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("join"))
        
        assertTrue(result)
        verify(mockGameManager).handleRejoin(mockPlayer)
        // 再参加成功時は追加のメッセージは送信されない
        verify(mockPlayer, never()).sendMessage("§cゲーム進行中は新規参加できません。")
    }
    
    @Test
    fun `leave コマンドが正しく動作することを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        `when`(mockGameManager.getPlayerRole(mockPlayer)).thenReturn(null)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("leave"))
        
        assertTrue(result)
        verify(mockGameManager).removePlayer(mockPlayer, true)
    }
    
    @Test
    fun `role コマンドでゲーム開始後に役割変更が拒否されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.RUNNING)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("role", "hunter"))
        
        assertTrue(result)
        verify(mockPlayer).sendMessage("§cゲーム開始後は役割を変更できません。")
    }
    
    @Test
    fun `role コマンドで引数が不足している場合にエラーメッセージが表示されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("role"))
        
        assertTrue(result)
        verify(mockPlayer).sendMessage("§c使用法: /manhunt role <runner|hunter|spectator>")
    }
    
    @Test
    fun `role コマンドで無効な役割が指定された場合にエラーメッセージが表示されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("role", "invalid"))
        
        assertTrue(result)
        verify(mockPlayer).sendMessage("§c無効な役割です。runner, hunter, spectator のいずれかを指定してください。")
    }
    
    @Test
    fun `role コマンドで正しく役割が変更されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("role", "runner"))
        
        assertTrue(result)
        verify(mockGameManager).setPlayerRole(mockPlayer, PlayerRole.RUNNER)
        verify(mockPlayer).sendMessage("§a役割を逃げる人に変更しました！")
    }
    
    @Test
    fun `start コマンドで権限がない場合にエラーメッセージが表示されることを確認`() {
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("start"))
        
        assertTrue(result)
        verify(mockSender).sendMessage("§cこのコマンドを実行する権限がありません。")
    }
    
    @Test
    fun `start コマンドで管理者がゲームを強制開始できることを確認`() {
        `when`(mockSender.hasPermission("manhunt.admin")).thenReturn(true)
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("start"))
        
        assertTrue(result)
        verify(mockGameManager).forceStartGame()
        verify(mockSender).sendMessage("§aゲームを強制開始しました！")
    }
    
    @Test
    fun `compass コマンドでゲーム進行中でない場合にエラーメッセージが表示されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("compass"))
        
        assertTrue(result)
        verify(mockPlayer).sendMessage("§cゲーム進行中のみコンパスを取得できます。")
    }
    
    @Test
    fun `compass コマンドで正しくコンパスが配布されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.RUNNING)
        
        val result = manhuntCommand.onCommand(mockPlayer, mockCommand, "manhunt", arrayOf("compass"))
        
        assertTrue(result)
        verify(mockCompassTracker).giveCompass(mockPlayer)
    }
    
    @Test
    fun `status コマンドでゲーム状況が表示されることを確認`() {
        `when`(mockGameManager.getGameState()).thenReturn(GameState.WAITING)
        `when`(mockGameManager.getMinPlayers()).thenReturn(2)
        `when`(mockGameManager.getAllRunners()).thenReturn(listOf())
        `when`(mockGameManager.getAllHunters()).thenReturn(listOf())
        `when`(mockGameManager.getAllSpectators()).thenReturn(listOf())
        
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("status"))
        
        assertTrue(result)
        verify(mockSender).sendMessage("§6=== Manhunt ゲーム状況 ===")
        verify(mockSender).sendMessage("§eゲーム状態: 待機中")
        verify(mockSender).sendMessage("§e最小プレイヤー数: 2")
    }
    
    @Test
    fun `sethunter コマンドで存在しないプレイヤーが指定された場合にエラーメッセージが表示されることを確認`() {
        mockStatic(Bukkit::class.java).use { bukkit ->
            `when`(mockSender.hasPermission("manhunt.admin")).thenReturn(true)
            bukkit.`when`<Player> { Bukkit.getPlayer("NonExistentPlayer") }.thenReturn(null)
            
            val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("sethunter", "NonExistentPlayer"))
            
            assertTrue(result)
            verify(mockSender).sendMessage("§cプレイヤー 'NonExistentPlayer' がオンラインではありません。")
        }
    }
    
    @Test
    fun `minplayers コマンドで引数なしの場合に現在値が表示されることを確認`() {
        `when`(mockSender.hasPermission("manhunt.admin")).thenReturn(true)
        `when`(mockGameManager.getMinPlayers()).thenReturn(3)
        
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("minplayers"))
        
        assertTrue(result)
        verify(mockSender).sendMessage("§c現在の最小プレイヤー数: 3")
        verify(mockSender).sendMessage("§c変更するには: /manhunt minplayers <数値>")
    }
    
    @Test
    fun `minplayers コマンドで無効な数値が指定された場合にエラーメッセージが表示されることを確認`() {
        `when`(mockSender.hasPermission("manhunt.admin")).thenReturn(true)
        
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("minplayers", "invalid"))
        
        assertTrue(result)
        verify(mockSender).sendMessage("§c'invalid' は有効な数値ではありません。")
    }
    
    @Test
    fun `minplayers コマンドで範囲外の数値が指定された場合にエラーメッセージが表示されることを確認`() {
        `when`(mockSender.hasPermission("manhunt.admin")).thenReturn(true)
        
        val result1 = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("minplayers", "1"))
        val result2 = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("minplayers", "101"))
        
        assertTrue(result1)
        assertTrue(result2)
        verify(mockSender).sendMessage("§c最小プレイヤー数は2以上である必要があります。")
        verify(mockSender).sendMessage("§c最小プレイヤー数は100以下である必要があります。")
    }
    
    @Test
    fun `reload コマンドが正しく動作することを確認`() {
        `when`(mockSender.hasPermission("manhunt.admin")).thenReturn(true)
        
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("reload"))
        
        assertTrue(result)
        verify(mockSender).sendMessage("§7設定ファイルをリロードしています...")
        verify(mockSender).sendMessage("§a設定ファイルのリロードが完了しました。")
    }
    
    @Test
    fun `不明なコマンドでエラーメッセージが表示されることを確認`() {
        val result = manhuntCommand.onCommand(mockSender, mockCommand, "manhunt", arrayOf("unknown"))
        
        assertTrue(result)
        verify(mockSender).sendMessage("§c不明なコマンドです。/manhunt help で使用法を確認してください。")
    }
}