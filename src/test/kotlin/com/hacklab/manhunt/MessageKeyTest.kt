package com.hacklab.manhunt

import com.hacklab.manhunt.utils.MessageKeyChecker
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * メッセージキーの整合性を自動的にテストするクラス
 * このテストは、コードで使用されているすべてのメッセージキーが
 * ja.ymlとen.ymlの両方に定義されていることを保証します
 */
class MessageKeyTest {
    
    @Test @Disabled
    fun `all message keys should be defined in both language files`() {
        // プロジェクトのルートディレクトリを取得
        val projectRoot = File(System.getProperty("user.dir"))
        
        // メッセージキーをチェック
        val result = MessageKeyChecker.checkMessageKeys(projectRoot)
        
        // エラーがある場合は詳細を出力
        if (result.hasErrors()) {
            println(result.getErrorMessage())
            
            // 自動修正の提案を生成
            if (result.missingInJa.isNotEmpty() || result.missingInEn.isNotEmpty()) {
                println("\n🔧 Auto-fix suggestions:")
                println("Add the following to your message files:\n")
                
                if (result.missingInJa.isNotEmpty()) {
                    println("# Add to ja.yml:")
                    generateYamlSuggestions(result.missingInJa, "ja")
                }
                
                if (result.missingInEn.isNotEmpty()) {
                    println("\n# Add to en.yml:")
                    generateYamlSuggestions(result.missingInEn, "en")
                }
            }
        }
        
        // テストの成功/失敗を判定
        assertFalse(result.hasErrors(), 
            "Found ${result.missingInJa.size + result.missingInEn.size} missing message keys. See output above for details.")
    }
    
    private fun generateYamlSuggestions(missingKeys: Set<String>, language: String) {
        // キーを階層構造に変換
        val hierarchy = mutableMapOf<String, Any>()
        
        missingKeys.sorted().forEach { key ->
            val parts = key.split(".")
            var current: MutableMap<String, Any> = hierarchy
            
            parts.forEachIndexed { index, part ->
                if (index == parts.size - 1) {
                    // 最後の部分は値
                    current[part] = getDefaultValue(key, language)
                } else {
                    // 中間部分は階層
                    current = current.getOrPut(part) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
                }
            }
        }
        
        // YAML形式で出力
        printYamlHierarchy(hierarchy, 0)
    }
    
    private fun printYamlHierarchy(map: Map<String, Any>, indent: Int) {
        map.forEach { (key, value) ->
            val spaces = " ".repeat(indent)
            when (value) {
                is String -> println("$spaces$key: '$value'")
                is Map<*, *> -> {
                    println("$spaces$key:")
                    @Suppress("UNCHECKED_CAST")
                    printYamlHierarchy(value as Map<String, Any>, indent + 2)
                }
            }
        }
    }
    
    private fun getDefaultValue(key: String, language: String): String {
        // キーに基づいてデフォルト値を生成
        val lastPart = key.substringAfterLast(".")
        
        return when {
            key.contains("reset-countdown") -> {
                if (language == "ja") "§6次のゲーム開始まで: §f{time}"
                else "§6Next game starts in: §f{time}"
            }
            key.contains("error") || key.contains("invalid") -> {
                if (language == "ja") "§c[エラー: $lastPart]"
                else "§c[Error: $lastPart]"
            }
            key.contains("success") -> {
                if (language == "ja") "§a[成功: $lastPart]"
                else "§a[Success: $lastPart]"
            }
            else -> {
                if (language == "ja") "§7[未翻訳: $lastPart]"
                else "§7[Missing: $lastPart]"
            }
        }
    }
}