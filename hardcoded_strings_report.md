# Hardcoded Strings Report for Minecraft Manhunt Plugin

## Summary
This report identifies hardcoded Japanese and English text in the Kotlin source files that should be moved to the MessageManager system for proper internationalization.

## Critical Issues Found

### 1. EventListener.kt
- **Line 486**: `player?.sendMessage("§cゲーム中はコンパスをクラフトできません。")`
  - Japanese hardcoded message when preventing compass crafting
  - Should use MessageManager with key like `game.compass-craft-prevented`

### 2. ManhuntCommand.kt
- **Line 53**: `sender.sendMessage("§a[Manhunt] Diagnostic information has been output to the server console.")`
- **Line 434**: `sender.sendMessage("§6[Manhunt] §eStarting message validation...")`
- **Line 448**: `sender.sendMessage("§6[Manhunt] §aValidation complete!")`
- **Line 451**: `sender.sendMessage("§c❌ Found missing message keys!")`
- **Line 454**: `sender.sendMessage("§e  Missing in ja.yml: ${result.missingInJa.size} keys")`
- **Line 456**: `sender.sendMessage("§7    - $key")`
- **Line 459**: `sender.sendMessage("§7    ... and ${result.missingInJa.size - 5} more")`
- **Line 464**: `sender.sendMessage("§e  Missing in en.yml: ${result.missingInEn.size} keys")`
- **Line 466**: `sender.sendMessage("§7    - $key")`
- **Line 469**: `sender.sendMessage("§7    ... and ${result.missingInEn.size - 5} more")`
- **Line 473**: `sender.sendMessage("§eCheck console for detailed report.")`
- **Line 475**: `sender.sendMessage("§a✅ All message keys are properly defined!")`
- **Line 480**: `sender.sendMessage("§c[Manhunt] Error during validation: ${e.message}")`
- **Lines 598, 606, 613**: `sender.sendMessage("")` (empty lines in help command)

### 3. ConfigManager.kt (Japanese Log Messages)
- **Line 120**: `plugin.logger.warning("最小プレイヤー数が無効だったため、2に修正しました。")`
- **Line 133**: `plugin.logger.warning("近接警告レベルが無効だったため、デフォルト値に修正しました。")`
- **Line 141**: `plugin.logger.warning("近接警告レベルの順序が無効だったため、デフォルト値に修正しました。")`
- **Line 151**: `plugin.logger.warning("コンパス更新間隔が無効だったため、1秒に修正しました。")`
- **Line 157**: `plugin.logger.warning("近接チェック間隔が無効だったため、1秒に修正しました。")`
- **Line 165**: `plugin.logger.warning("最小表示距離が無効だったため、5メートルに修正しました。")`
- **Line 176**: `plugin.logger.warning("スポーン最小半径が無効だったため、100メートルに修正しました。")`

### 4. VirtualCompass.kt (Hardcoded Color Codes and Formatting)
- **Lines 216-218, 241-243**: Distance formatting with hardcoded color codes
  ```kotlin
  distance < 10 -> "§c§l${distance.toInt()}m"
  distance < 50 -> "§e§l${distance.toInt()}m"
  else -> "§a§l${distance.toInt()}m"
  ```
- **Lines 223-224**: Title formatting
  ```kotlin
  "§6§l${target.name}"
  "$formattedDistance §7| ${getDirectionArrow(direction)}"
  ```
- **Lines 248-249**: Multi-target title formatting
  ```kotlin
  "§b§l[${currentIndex}/${totalTargets}] §6§l${target.name}"
  "$formattedDistance §7| ${getDirectionArrow(direction)} §8| ${messageManager.getMessage("virtual-compass.target-switch")}"
  ```

### 5. RoleSelectorMenu.kt
- **Line 81**: `val prefix = if (isSelected) "§a§l✓ " else "§7"`
  - Hardcoded check mark and color for selected state

### 6. SpectatorMenu.kt
- **Line 104**: `meta.setDisplayName("$roleColor${playerInfo.player.name}")`
  - Player name formatting should use MessageManager

### 7. UIManager.kt
- **Line 618**: `player.sendMessage("$title${if (subtitle.isNotEmpty()) " - $subtitle" else ""}")`
  - Fallback chat message formatting

### 8. TeamChatCommand.kt
- **Line 124**: `gameManager.getPlugin().logger.info("TeamChat [${senderRole.name}] ${sender.name}: $message (${teammates.size} recipients)")`
  - Log message formatting

### 9. ShopCommand.kt
- **Lines 97, 104, 111**: `player.sendMessage("")` (empty lines in help command)

### 10. Comments in Japanese
Multiple files contain Japanese comments (e.g., SpectatorMenu.kt, BuddySystem.kt) which is acceptable according to the coding standards, but they should be consistent with the codebase style.

## Recommendations

1. **Create message keys** for all hardcoded strings identified above
2. **Update both ja.yml and en.yml** with appropriate translations
3. **Replace hardcoded strings** with `messageManager.getMessage()` calls
4. **Standardize color formatting** by including color codes in message files
5. **Create formatting templates** for complex messages (like titles with multiple parameters)
6. **Logger messages** should also be internationalized or at least kept in English for consistency

## Priority Fixes

1. **EventListener.kt line 486** - The only Japanese message visible to players in-game
2. **ManhuntCommand.kt validation messages** - All validation-related messages should use MessageManager
3. **VirtualCompass.kt** - Title and distance formatting should be template-based
4. **ConfigManager.kt** - Logger warnings should be in English or use a logging framework that supports i18n

## Message Key Suggestions

```yaml
# For EventListener.kt
game:
  compass-craft-prevented: "Cannot craft compass during the game"

# For ManhuntCommand.kt
command:
  diagnose:
    output-to-console: "[Manhunt] Diagnostic information has been output to the server console."
  validation:
    starting: "[Manhunt] Starting message validation..."
    complete: "[Manhunt] Validation complete!"
    found-missing: "❌ Found missing message keys!"
    missing-in-file: "  Missing in {file}: {count} keys"
    missing-key: "    - {key}"
    and-more: "    ... and {count} more"
    check-console: "Check console for detailed report."
    all-valid: "✅ All message keys are properly defined!"
    error: "[Manhunt] Error during validation: {error}"

# For VirtualCompass.kt
virtual-compass:
  distance:
    close: "{distance}m"  # With red color in message file
    medium: "{distance}m" # With yellow color in message file
    far: "{distance}m"    # With green color in message file
  title:
    single-target: "{name}"
    multi-target: "[{current}/{total}] {name}"
  subtitle:
    format: "{distance} | {direction}"
    with-hint: "{distance} | {direction} | {hint}"

# For RoleSelectorMenu.kt
role-selector:
  selected-prefix: "✓ "
  unselected-prefix: ""
```