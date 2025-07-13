package com.hacklab.manhunt.utils

import java.io.File

/**
 * メッセージキーの整合性を自動チェックするユーティリティ
 * コード変更時に呼び出して、メッセージファイルの不整合を検出する
 */
object MessageKeyChecker {
    
    private val messageKeysInCode = mutableSetOf<String>()
    private val messageKeysInYaml = mutableMapOf<String, MutableSet<String>>()
    
    /**
     * プロジェクト全体のメッセージキーをチェック
     */
    fun checkMessageKeys(projectRoot: File): CheckResult {
        messageKeysInCode.clear()
        messageKeysInYaml.clear()
        
        // コード内のメッセージキーを収集
        collectKeysFromCode(projectRoot)
        
        // YAMLファイルのメッセージキーを収集
        collectKeysFromYaml(projectRoot)
        
        // 不足しているキーを検出
        val missingInJa = mutableSetOf<String>()
        val missingInEn = mutableSetOf<String>()
        
        messageKeysInCode.forEach { key ->
            if (!messageKeysInYaml["ja"]?.contains(key)!!) {
                missingInJa.add(key)
            }
            if (!messageKeysInYaml["en"]?.contains(key)!!) {
                missingInEn.add(key)
            }
        }
        
        return CheckResult(
            missingInJa = missingInJa,
            missingInEn = missingInEn,
            totalKeysInCode = messageKeysInCode.size,
            keysInJa = messageKeysInYaml["ja"]?.size ?: 0,
            keysInEn = messageKeysInYaml["en"]?.size ?: 0
        )
    }
    
    private fun collectKeysFromCode(projectRoot: File) {
        val srcDir = File(projectRoot, "src/main/kotlin")
        if (srcDir.exists()) {
            srcDir.walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    extractMessageKeys(file)
                }
        }
    }
    
    private fun extractMessageKeys(file: File) {
        val content = file.readText()
        
        // getMessage呼び出しのパターン
        val patterns = listOf(
            // getMessage("key")
            """getMessage\s*\(\s*"([^"]+)"""".toRegex(),
            // getMessage(player, "key")
            """getMessage\s*\([^,]+,\s*"([^"]+)"""".toRegex(),
            // messageManager.getMessage("key")
            """messageManager\.getMessage\s*\(\s*"([^"]+)"""".toRegex(),
            // messageManager.getMessage(player, "key")
            """messageManager\.getMessage\s*\([^,]+,\s*"([^"]+)"""".toRegex()
        )
        
        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                val key = match.groupValues[1]
                // 動的キー（${}を含む）と明らかに誤検出のものは除外
                if (!key.contains("\${") && !key.contains("$") && 
                    key != "key" && !key.contains("/") && key.length > 2) {
                    messageKeysInCode.add(key)
                }
            }
        }
    }
    
    private fun collectKeysFromYaml(projectRoot: File) {
        val messageFiles = mapOf(
            "ja" to File(projectRoot, "src/main/resources/messages/ja.yml"),
            "en" to File(projectRoot, "src/main/resources/messages/en.yml")
        )
        
        messageFiles.forEach { (lang, file) ->
            if (file.exists()) {
                val keys = mutableSetOf<String>()
                parseYamlFile(file, "", keys)
                messageKeysInYaml[lang] = keys
            }
        }
    }
    
    /**
     * シンプルなYAMLパーサー（テスト環境用）
     * BukkitのYamlConfigurationが使えない環境でも動作する
     */
    private fun parseYamlFile(file: File, prefix: String, result: MutableSet<String>) {
        var currentIndent = -1
        val indentStack = mutableListOf<Pair<Int, String>>()
        
        file.readLines().forEachIndexed { lineNum, line ->
            // コメントと空行をスキップ
            if (line.trim().isEmpty() || line.trim().startsWith("#")) return@forEachIndexed
            
            // インデントレベルを計算
            val indent = line.takeWhile { it == ' ' }.length
            val trimmedLine = line.trim()
            
            // key: value形式を解析
            if (trimmedLine.contains(":")) {
                val parts = trimmedLine.split(":", limit = 2)
                val key = parts[0].trim()
                val value = if (parts.size > 1) parts[1].trim() else ""
                
                // インデントスタックを調整
                while (indentStack.isNotEmpty() && indentStack.last().first >= indent) {
                    indentStack.removeLast()
                }
                
                // 現在のパスを構築
                val currentPath = if (indentStack.isEmpty()) {
                    key
                } else {
                    indentStack.joinToString(".") { it.second } + "." + key
                }
                
                if (value.isNotEmpty() && !value.startsWith("'") && !value.startsWith("\"")) {
                    // 値がある場合はキーとして追加
                    result.add(currentPath)
                } else if (value.startsWith("'") || value.startsWith("\"")) {
                    // 文字列値がある場合もキーとして追加
                    result.add(currentPath)
                } else {
                    // 値がない場合は階層として扱う
                    indentStack.add(indent to key)
                }
            }
        }
    }
    
    data class CheckResult(
        val missingInJa: Set<String>,
        val missingInEn: Set<String>,
        val totalKeysInCode: Int,
        val keysInJa: Int,
        val keysInEn: Int
    ) {
        fun hasErrors(): Boolean = missingInJa.isNotEmpty() || missingInEn.isNotEmpty()
        
        fun getErrorMessage(): String {
            if (!hasErrors()) return "✅ All message keys are properly defined!"
            
            val sb = StringBuilder()
            sb.appendLine("❌ Missing message keys detected:")
            
            if (missingInJa.isNotEmpty()) {
                sb.appendLine("\n🇯🇵 Missing in ja.yml (${missingInJa.size} keys):")
                missingInJa.sorted().forEach { key ->
                    sb.appendLine("  - $key")
                }
            }
            
            if (missingInEn.isNotEmpty()) {
                sb.appendLine("\n🇺🇸 Missing in en.yml (${missingInEn.size} keys):")
                missingInEn.sorted().forEach { key ->
                    sb.appendLine("  - $key")
                }
            }
            
            sb.appendLine("\n📊 Summary:")
            sb.appendLine("  Total keys in code: $totalKeysInCode")
            sb.appendLine("  Keys in ja.yml: $keysInJa")
            sb.appendLine("  Keys in en.yml: $keysInEn")
            
            return sb.toString()
        }
    }
}