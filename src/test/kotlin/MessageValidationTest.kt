import com.hacklab.manhunt.utils.MessageValidator
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader

/**
 * Standalone message validation test
 * Run this to validate all message keys without starting the server
 */
fun main() {
    println("Starting Message Key Validation...")
    println("================================")
    
    val projectRoot = File(System.getProperty("user.dir"))
    val sourceDir = File(projectRoot, "src/main/kotlin")
    val resourceDir = File(projectRoot, "src/main/resources")
    
    if (!sourceDir.exists()) {
        println("Error: Source directory not found at ${sourceDir.absolutePath}")
        return
    }
    
    // Extract all getMessage() calls from code
    val codeKeys = extractMessageKeysFromCode(sourceDir)
    println("\nFound ${codeKeys.size} getMessage() calls in code")
    
    // Load message keys from YAML files
    val jaKeys = loadMessageKeys(File(resourceDir, "messages/ja.yml"))
    val enKeys = loadMessageKeys(File(resourceDir, "messages/en.yml"))
    
    println("Found ${jaKeys.size} keys in ja.yml")
    println("Found ${enKeys.size} keys in en.yml")
    
    // Find missing keys
    val missingInJa = codeKeys - jaKeys
    val missingInEn = codeKeys - enKeys
    val missingInBoth = missingInJa.intersect(missingInEn)
    
    // Find unused keys
    val unusedInJa = jaKeys - codeKeys
    val unusedInEn = enKeys - codeKeys
    
    // Find language inconsistencies
    val onlyInJa = jaKeys - enKeys
    val onlyInEn = enKeys - jaKeys
    
    // Print results
    println("\n=== VALIDATION RESULTS ===")
    
    if (missingInBoth.isNotEmpty()) {
        println("\n❌ CRITICAL: Keys missing in BOTH languages:")
        missingInBoth.sorted().forEach { key ->
            println("  - $key")
        }
    }
    
    if ((missingInJa - missingInBoth).isNotEmpty()) {
        println("\n⚠️  Keys missing only in ja.yml:")
        (missingInJa - missingInBoth).sorted().forEach { key ->
            println("  - $key")
        }
    }
    
    if ((missingInEn - missingInBoth).isNotEmpty()) {
        println("\n⚠️  Keys missing only in en.yml:")
        (missingInEn - missingInBoth).sorted().forEach { key ->
            println("  - $key")
        }
    }
    
    if (onlyInJa.isNotEmpty()) {
        println("\n📝 Keys only in ja.yml (not in en.yml):")
        onlyInJa.sorted().take(10).forEach { key ->
            println("  - $key")
        }
        if (onlyInJa.size > 10) {
            println("  ... and ${onlyInJa.size - 10} more")
        }
    }
    
    if (onlyInEn.isNotEmpty()) {
        println("\n📝 Keys only in en.yml (not in ja.yml):")
        onlyInEn.sorted().take(10).forEach { key ->
            println("  - $key")
        }
        if (onlyInEn.size > 10) {
            println("  ... and ${onlyInEn.size - 10} more")
        }
    }
    
    val commonUnused = unusedInJa.intersect(unusedInEn)
    if (commonUnused.isNotEmpty()) {
        println("\n💡 Potentially unused keys (in both languages): ${commonUnused.size}")
        commonUnused.sorted().take(5).forEach { key ->
            println("  - $key")
        }
        if (commonUnused.size > 5) {
            println("  ... and ${commonUnused.size - 5} more")
        }
    }
    
    // Summary
    println("\n=== SUMMARY ===")
    val hasIssues = missingInBoth.isNotEmpty() || missingInJa.isNotEmpty() || missingInEn.isNotEmpty()
    if (hasIssues) {
        println("❌ Validation FAILED")
        println("  - Critical missing keys: ${missingInBoth.size}")
        println("  - Missing in ja.yml: ${missingInJa.size}")
        println("  - Missing in en.yml: ${missingInEn.size}")
    } else {
        println("✅ Validation PASSED")
        println("  All message keys are properly defined!")
    }
    
    println("  - Language inconsistencies: ${onlyInJa.size + onlyInEn.size}")
    println("  - Potentially unused keys: ${commonUnused.size}")
    
    // Generate fix suggestions
    if (missingInJa.isNotEmpty() || missingInEn.isNotEmpty()) {
        println("\n=== SUGGESTED FIXES ===")
        
        if (missingInJa.isNotEmpty()) {
            println("\nAdd to ja.yml:")
            missingInJa.sorted().forEach { key ->
                if (key in enKeys) {
                    println("$key: '§c[TODO: Translate from English]'")
                } else {
                    println("$key: '§c[TODO: Add Japanese message]'")
                }
            }
        }
        
        if (missingInEn.isNotEmpty()) {
            println("\nAdd to en.yml:")
            missingInEn.sorted().forEach { key ->
                if (key in jaKeys) {
                    println("$key: '§c[TODO: Translate from Japanese]'")
                } else {
                    println("$key: '§c[TODO: Add English message]'")
                }
            }
        }
    }
}

fun extractMessageKeysFromCode(sourceDir: File): Set<String> {
    val messageKeys = mutableSetOf<String>()
    
    sourceDir.walkTopDown()
        .filter { it.extension == "kt" }
        .forEach { file ->
            val content = file.readText()
            
            // Various getMessage patterns
            val patterns = listOf(
                Regex("""getMessage\s*\(\s*"([^"]+)"\s*\)"""),
                Regex("""getMessage\s*\(\s*[^,]+,\s*"([^"]+)"\s*[,)]"""),
                Regex("""getMessage\s*\(\s*[^,]+,\s*"([^"]+)"\s*,"""),
                Regex("""getMessage\s*\(\s*null\s*,\s*"([^"]+)"\s*[,)]"""),
                Regex("""getMessage\s*\(\s*sender\s*,\s*"([^"]+)"\s*[,)]"""),
                Regex("""getMessage\s*\(\s*player\s*,\s*"([^"]+)"\s*[,)]""")
            )
            
            patterns.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    val key = match.groupValues[1]
                    messageKeys.add(key)
                }
            }
        }
    
    return messageKeys
}

fun loadMessageKeys(file: File): Set<String> {
    val keys = mutableSetOf<String>()
    
    if (!file.exists()) {
        println("Warning: File not found: ${file.absolutePath}")
        return keys
    }
    
    try {
        val yaml = YamlConfiguration.loadConfiguration(file)
        extractKeysRecursively(yaml, "", keys)
    } catch (e: Exception) {
        println("Error loading ${file.name}: ${e.message}")
    }
    
    return keys
}

fun extractKeysRecursively(yaml: YamlConfiguration, prefix: String, keys: MutableSet<String>) {
    yaml.getKeys(false).forEach { key ->
        val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
        
        when (val value = yaml.get(key)) {
            is String -> keys.add(fullKey)
            else -> {
                val section = yaml.getConfigurationSection(key)
                if (section != null) {
                    val subYaml = YamlConfiguration()
                    section.getKeys(false).forEach { subKey ->
                        subYaml.set(subKey, section.get(subKey))
                    }
                    extractKeysRecursively(subYaml, fullKey, keys)
                }
            }
        }
    }
}