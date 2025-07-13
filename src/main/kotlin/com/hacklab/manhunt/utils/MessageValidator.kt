package com.hacklab.manhunt.utils

import com.hacklab.manhunt.MessageManager
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader

/**
 * メッセージキーの整合性を検証するユーティリティクラス
 */
class MessageValidator(
    private val messageManager: MessageManager,
    private val pluginFolder: File
) {
    companion object {
        private const val ANSI_RED = "\u001B[31m"
        private const val ANSI_GREEN = "\u001B[32m"
        private const val ANSI_YELLOW = "\u001B[33m"
        private const val ANSI_RESET = "\u001B[0m"
    }

    /**
     * 全ての getMessage() 呼び出しを抽出
     */
    fun extractAllGetMessageCalls(): Set<String> {
        val messageKeys = mutableSetOf<String>()
        val sourceDir = File(pluginFolder.parentFile.parentFile, "src/main/kotlin")
        
        if (!sourceDir.exists()) {
            println("${ANSI_RED}Source directory not found: $sourceDir${ANSI_RESET}")
            return messageKeys
        }

        // 再帰的に全ての .kt ファイルを検索
        sourceDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                extractKeysFromFile(file, messageKeys)
            }

        return messageKeys
    }

    /**
     * ファイルから getMessage() の呼び出しを抽出
     */
    private fun extractKeysFromFile(file: File, messageKeys: MutableSet<String>) {
        val content = file.readText()
        
        // getMessage の様々なパターンをマッチング
        val patterns = listOf(
            // getMessage("key")
            Regex("""getMessage\s*\(\s*"([^"]+)"\s*\)"""),
            // getMessage(player, "key")
            Regex("""getMessage\s*\(\s*[^,]+,\s*"([^"]+)"\s*[,)]"""),
            // getMessage(player, "key", ...)
            Regex("""getMessage\s*\(\s*[^,]+,\s*"([^"]+)"\s*,"""),
            // getMessage(null, "key")
            Regex("""getMessage\s*\(\s*null\s*,\s*"([^"]+)"\s*[,)]"""),
            // getMessage(sender, "key")
            Regex("""getMessage\s*\(\s*sender\s*,\s*"([^"]+)"\s*[,)]""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                val key = match.groupValues[1]
                messageKeys.add(key)
            }
        }
    }

    /**
     * 言語ファイルから全てのキーを抽出
     */
    fun extractKeysFromLanguageFile(language: String): Set<String> {
        val keys = mutableSetOf<String>()
        
        try {
            // リソースから読み込み
            val resource = "/messages/$language.yml"
            val inputStream = javaClass.getResourceAsStream(resource)
            
            if (inputStream != null) {
                val yaml = YamlConfiguration.loadConfiguration(InputStreamReader(inputStream))
                extractKeysRecursively(yaml, "", keys)
            } else {
                // ファイルシステムから読み込み
                val file = File(pluginFolder, "messages/$language.yml")
                if (file.exists()) {
                    val yaml = YamlConfiguration.loadConfiguration(file)
                    extractKeysRecursively(yaml, "", keys)
                }
            }
        } catch (e: Exception) {
            println("${ANSI_RED}Error loading $language.yml: ${e.message}${ANSI_RESET}")
        }
        
        return keys
    }

    /**
     * YAMLから再帰的にキーを抽出
     */
    private fun extractKeysRecursively(config: YamlConfiguration, prefix: String, keys: MutableSet<String>) {
        config.getKeys(false).forEach { key ->
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            
            when (val value = config.get(key)) {
                is String -> keys.add(fullKey)
                is YamlConfiguration, is org.bukkit.configuration.ConfigurationSection -> {
                    val section = config.getConfigurationSection(key)
                    if (section != null) {
                        val subConfig = YamlConfiguration()
                        section.getKeys(false).forEach { subKey ->
                            subConfig.set(subKey, section.get(subKey))
                        }
                        extractKeysRecursively(subConfig, fullKey, keys)
                    }
                }
            }
        }
    }

    /**
     * メッセージキーの検証を実行
     */
    fun validateMessages(): ValidationResult {
        println("\n${ANSI_YELLOW}=== Message Key Validation ===${ANSI_RESET}")
        
        // コードから getMessage 呼び出しを抽出
        val codeKeys = extractAllGetMessageCalls()
        println("Found ${codeKeys.size} getMessage() calls in code")
        
        // 言語ファイルからキーを抽出
        val jaKeys = extractKeysFromLanguageFile("ja")
        val enKeys = extractKeysFromLanguageFile("en")
        
        println("Found ${jaKeys.size} keys in ja.yml")
        println("Found ${enKeys.size} keys in en.yml")
        
        // 不足しているキーを検出
        val missingInJa = codeKeys - jaKeys
        val missingInEn = codeKeys - enKeys
        val missingInBoth = missingInJa.intersect(missingInEn)
        
        // 未使用のキーを検出
        val unusedInJa = jaKeys - codeKeys
        val unusedInEn = enKeys - codeKeys
        
        // 言語間の不整合を検出
        val onlyInJa = jaKeys - enKeys
        val onlyInEn = enKeys - jaKeys
        
        // 結果を表示
        printValidationResults(
            missingInJa, missingInEn, missingInBoth,
            unusedInJa, unusedInEn,
            onlyInJa, onlyInEn
        )
        
        return ValidationResult(
            codeKeys = codeKeys,
            jaKeys = jaKeys,
            enKeys = enKeys,
            missingInJa = missingInJa,
            missingInEn = missingInEn,
            unusedInJa = unusedInJa,
            unusedInEn = unusedInEn,
            onlyInJa = onlyInJa,
            onlyInEn = onlyInEn
        )
    }

    /**
     * 検証結果を表示
     */
    private fun printValidationResults(
        missingInJa: Set<String>,
        missingInEn: Set<String>,
        missingInBoth: Set<String>,
        unusedInJa: Set<String>,
        unusedInEn: Set<String>,
        onlyInJa: Set<String>,
        onlyInEn: Set<String>
    ) {
        // 両方の言語で不足しているキー（重要）
        if (missingInBoth.isNotEmpty()) {
            println("\n${ANSI_RED}❌ CRITICAL: Keys missing in BOTH languages:${ANSI_RESET}")
            missingInBoth.sorted().forEach { key ->
                println("  - $key")
            }
        }
        
        // 日本語のみで不足
        if (missingInJa.isNotEmpty() && missingInJa != missingInBoth) {
            println("\n${ANSI_YELLOW}⚠️  Keys missing in ja.yml:${ANSI_RESET}")
            (missingInJa - missingInBoth).sorted().forEach { key ->
                println("  - $key")
            }
        }
        
        // 英語のみで不足
        if (missingInEn.isNotEmpty() && missingInEn != missingInBoth) {
            println("\n${ANSI_YELLOW}⚠️  Keys missing in en.yml:${ANSI_RESET}")
            (missingInEn - missingInBoth).sorted().forEach { key ->
                println("  - $key")
            }
        }
        
        // 言語間の不整合
        if (onlyInJa.isNotEmpty()) {
            println("\n${ANSI_YELLOW}📝 Keys only in ja.yml (not in en.yml):${ANSI_RESET}")
            onlyInJa.sorted().forEach { key ->
                println("  - $key")
            }
        }
        
        if (onlyInEn.isNotEmpty()) {
            println("\n${ANSI_YELLOW}📝 Keys only in en.yml (not in ja.yml):${ANSI_RESET}")
            onlyInEn.sorted().forEach { key ->
                println("  - $key")
            }
        }
        
        // 未使用のキー（情報のみ）
        val commonUnused = unusedInJa.intersect(unusedInEn)
        if (commonUnused.isNotEmpty()) {
            println("\n${ANSI_YELLOW}💡 Potentially unused keys (in both languages):${ANSI_RESET}")
            commonUnused.sorted().take(10).forEach { key ->
                println("  - $key")
            }
            if (commonUnused.size > 10) {
                println("  ... and ${commonUnused.size - 10} more")
            }
        }
        
        // サマリー
        println("\n${ANSI_GREEN}=== Summary ===${ANSI_RESET}")
        val hasIssues = missingInBoth.isNotEmpty() || missingInJa.isNotEmpty() || missingInEn.isNotEmpty()
        if (hasIssues) {
            println("${ANSI_RED}❌ Validation FAILED${ANSI_RESET}")
            println("  - Critical missing keys: ${missingInBoth.size}")
            println("  - Missing in ja.yml: ${missingInJa.size}")
            println("  - Missing in en.yml: ${missingInEn.size}")
        } else {
            println("${ANSI_GREEN}✅ Validation PASSED${ANSI_RESET}")
            println("  All message keys are properly defined!")
        }
        
        println("  - Language inconsistencies: ${onlyInJa.size + onlyInEn.size}")
        println("  - Potentially unused keys: ${commonUnused.size}")
    }

    /**
     * 不足しているキーの修正案を生成
     */
    fun generateMissingKeySuggestions(result: ValidationResult) {
        if (result.missingInJa.isEmpty() && result.missingInEn.isEmpty()) {
            return
        }
        
        println("\n${ANSI_YELLOW}=== Suggested Fixes ===${ANSI_RESET}")
        
        // 両方で不足しているキーの提案
        if (result.missingInJa.isNotEmpty()) {
            println("\n${ANSI_YELLOW}Add to ja.yml:${ANSI_RESET}")
            result.missingInJa.sorted().forEach { key ->
                // 英語版が存在する場合はそれを参考に
                if (key in result.enKeys) {
                    println("$key: '§c[TODO: Translate from English]'")
                } else {
                    println("$key: '§c[TODO: Add Japanese message]'")
                }
            }
        }
        
        if (result.missingInEn.isNotEmpty()) {
            println("\n${ANSI_YELLOW}Add to en.yml:${ANSI_RESET}")
            result.missingInEn.sorted().forEach { key ->
                // 日本語版が存在する場合はそれを参考に
                if (key in result.jaKeys) {
                    println("$key: '§c[TODO: Translate from Japanese]'")
                } else {
                    println("$key: '§c[TODO: Add English message]'")
                }
            }
        }
    }

    /**
     * 検証結果のデータクラス
     */
    data class ValidationResult(
        val codeKeys: Set<String>,
        val jaKeys: Set<String>,
        val enKeys: Set<String>,
        val missingInJa: Set<String>,
        val missingInEn: Set<String>,
        val unusedInJa: Set<String>,
        val unusedInEn: Set<String>,
        val onlyInJa: Set<String>,
        val onlyInEn: Set<String>
    )
}