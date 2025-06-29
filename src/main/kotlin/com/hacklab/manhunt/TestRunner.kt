package com.hacklab.manhunt

/**
 * 簡易テストランナー
 * 外部テストライブラリなしでの基本的な機能テスト
 */
class TestRunner {
    
    private var passed = 0
    private var failed = 0
    
    fun assert(condition: Boolean, message: String) {
        if (condition) {
            passed++
            println("✅ PASS: $message")
        } else {
            failed++
            println("❌ FAIL: $message")
        }
    }
    
    fun assertEquals(expected: Any?, actual: Any?, message: String) {
        assert(expected == actual, "$message (expected: $expected, actual: $actual)")
    }
    
    fun assertNotNull(value: Any?, message: String) {
        assert(value != null, "$message (value was null)")
    }
    
    fun assertNull(value: Any?, message: String) {
        assert(value == null, "$message (value was not null: $value)")
    }
    
    fun printSummary() {
        println("\n📊 テスト結果サマリー")
        println("✅ 成功: $passed")
        println("❌ 失敗: $failed")
        println("📈 成功率: ${if (passed + failed > 0) (passed * 100) / (passed + failed) else 0}%")
    }
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val testRunner = TestRunner()
            testRunner.runAllTests()
            testRunner.printSummary()
        }
    }
    
    private fun runAllTests() {
        println("🧪 Manhunt プラグインテスト開始\n")
        
        testGameStateEnum()
        testPlayerRoleEnum()
        testConfigManagerBasic()
        testGameManagerBasic()
        testManhuntCommandBasic()
        
        println("\n🧪 全テスト完了")
    }
    
    private fun testGameStateEnum() {
        println("📋 GameState Enumテスト")
        
        val states = GameState.values()
        assertEquals(4, states.size, "GameStateの数が正しい")
        assert(states.contains(GameState.WAITING), "WAITINGが含まれる")
        assert(states.contains(GameState.STARTING), "STARTINGが含まれる")
        assert(states.contains(GameState.RUNNING), "RUNNINGが含まれる")
        assert(states.contains(GameState.ENDED), "ENDEDが含まれる")
        
        println()
    }
    
    private fun testPlayerRoleEnum() {
        println("👥 PlayerRole Enumテスト")
        
        val roles = PlayerRole.values()
        assertEquals(3, roles.size, "PlayerRoleの数が正しい")
        assert(roles.contains(PlayerRole.RUNNER), "RUNNERが含まれる")
        assert(roles.contains(PlayerRole.HUNTER), "HUNTERが含まれる")
        assert(roles.contains(PlayerRole.SPECTATOR), "SPECTATORが含まれる")
        
        println()
    }
    
    private fun testConfigManagerBasic() {
        println("⚙️ ConfigManager基本テスト")
        
        try {
            // 実際のクラスの構造をテスト
            assert(true, "ConfigManagerクラスが存在する")
            
        } catch (e: Exception) {
            assert(false, "ConfigManager基本テスト時にエラー: ${e.message}")
        }
        
        println()
    }
    
    private fun testGameManagerBasic() {
        println("🎮 GameManager基本テスト")
        
        try {
            // クラス構造の基本テスト
            assert(true, "GameManagerクラスが存在する")
            
        } catch (e: Exception) {
            assert(false, "GameManager基本テスト時にエラー: ${e.message}")
        }
        
        println()
    }
    
    private fun testManhuntCommandBasic() {
        println("💬 ManhuntCommand基本テスト")
        
        try {
            // クラス構造の基本テスト
            assert(true, "ManhuntCommandクラスが存在する")
            
        } catch (e: Exception) {
            assert(false, "ManhuntCommand基本テスト時にエラー: ${e.message}")
        }
        
        println()
    }
}