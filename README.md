# 🏃 Minecraft Manhunt Latest

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/man-hunt-latest?label=Downloads&logo=modrinth)](https://modrinth.com/plugin/man-hunt-latest)
[![GitHub Release](https://img.shields.io/github/v/release/0x48lab/minecraft_manhunt?label=Latest%20Release)](https://github.com/0x48lab/minecraft_manhunt/releases)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://www.minecraft.net/)
[![Platform](https://img.shields.io/badge/Platform-Spigot%20%7C%20Paper-orange.svg)](https://papermc.io/)

A comprehensive multiplayer Manhunt plugin for Minecraft servers featuring advanced tracking systems, economy integration, and full internationalization support.

## ✨ Features

### 🎯 Core Manhunt Gameplay
- **Dynamic Role System**: Players can be Hunters, Runners, or Spectators
- **Advanced Compass Tracking**: Physical compass-based tracking with target switching
- **Proximity Warning System**: Tiered warning system for Runners when Hunters approach
- **Automatic Game Management**: Smart game state handling with reconnection support

### 💰 Economy & Shop System
- **In-Game Currency**: Earn coins through gameplay actions
- **Comprehensive Shop**: 40+ items across 6 categories (Weapons, Armor, Tools, Consumables, Food, Special)
- **Role-Based Rewards**: Different earning methods for Hunters and Runners
- **Purchase Restrictions**: Item limits, cooldowns, and role-specific restrictions

### 🌍 Full Internationalization
- **Multi-Language Support**: Complete English and Japanese localization
- **Player-Specific Languages**: Individual language preferences
- **Localized Shop Items**: All items and descriptions fully translated
- **Dynamic UI**: All interfaces adapt to player language settings

### 📊 Advanced Statistics & Results
- **Detailed Player Statistics**: Track performance, earnings, and achievements
- **MVP System**: Automatic recognition of top performers
- **Comprehensive Game Results**: Multi-stage result presentation with visual effects
- **Team Rankings**: Performance-based team and individual rankings

### 🎮 Enhanced User Experience
- **Role Selection GUI**: Intuitive graphical role selection menu
- **Spectator Menu**: Easy teleportation system for spectators
- **Team Communication**: Private team chat and coordinate sharing
- **Buddy System**: Partner up with a teammate for enhanced coordination
- **Real-time UI**: Dynamic scoreboard, action bar, and boss bar displays

## 🎯 Game Objectives

- **🏃 Runners**: Defeat the Ender Dragon to achieve victory
- **🗡 Hunters**: Eliminate all Runners before they complete their objective
- **👁 Spectators**: Observe the game with full mobility and teleportation options

## 🛠 Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/man-hunt-latest)
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin using `/manhunt reload` after editing `config.yml`

## 📋 Requirements

- **Minecraft Version**: 1.21.4
- **Server Software**: Spigot or Paper
- **Java Version**: 21 or higher
- **Minimum Players**: 2 (configurable)

## 🎮 Quick Start

1. Join the server and run `/manhunt join`
2. Select your role using `/manhunt roles` (GUI menu)
3. Wait for minimum players and automatic game start
4. Use `/manhunt help` for all available commands

## ⚙️ Configuration

The plugin is highly configurable through `config.yml`:

```yaml
# Language Settings
language:
  default: "en"                    # Default language (ja/en)
  per-player: true                 # Per-player language settings

# Game Settings
game:
  min-players: 2                   # Minimum players to start
  start-countdown: 10              # Countdown before game start

# Economy Settings
economy:
  starting-balance: 0              # Starting money (0G)
  currency-unit: "G"               # Currency symbol
  hunter:
    damage-reward: 5               # Reward per damage point
    kill-reward: 150               # Reward per kill
  runner:
    survival-bonus: 0.05           # Survival bonus per second
    nether-reward: 200             # Nether entry reward
```

## 🎯 Commands

### Player Commands
- `/manhunt role <runner|hunter|spectator>` - Change role (waiting only)
- `/manhunt roles` - Open role selection GUI
- `/manhunt status` - Check game status
- `/manhunt compass` - Get tracking compass (Hunters only)
- `/manhunt spectate` - Open spectator menu (Spectators only)
- `/r <message>` - Team chat
- `/pos` - Share coordinates with team
- `/shop` - Open shop menu
- `/shop balance` - Check balance
- `/buddy <subcommand>` - Buddy system commands (during game only)

### Admin Commands
- `/manhunt start` - Force start game
- `/manhunt stop` or `/manhunt end` - Force stop game
- `/manhunt sethunter <player>` - Assign hunter role
- `/manhunt setrunner <player>` - Assign runner role
- `/manhunt setspectator <player>` - Assign spectator role
- `/manhunt minplayers <number>` - Set minimum players
- `/manhunt reload [config|shop|all]` - Reload configurations
- `/manhunt ui <toggle|status>` - Control UI displays
- `/manhunt respawntime <player> <seconds>` - Set custom respawn time
- `/manhunt reset` - Force reset game (after game ended only)
- `/manhunt give <player> <amount>` - Give money to player
- `/manhunt validate-messages` - Check message file integrity
- `/manhunt diagnose` - Output diagnostic information

## 🏆 Economy System

### Earning Methods

**🗡 Hunters:**
- 5G per damage point dealt to Runners
- 150G per Runner elimination
- 1.5G every 30 seconds (time bonus)

**🏃 Runners:**
- 1.5G every 30 seconds (survival bonus)
- 200G for reaching the Nether
- 300G for finding a fortress
- 500G for reaching the End
- 25G per diamond collected
- 20G for successful escapes

### Shop Categories
- **⚔️ Weapons**: Swords, axes, tridents
- **🛡️ Armor**: Full armor sets in various materials
- **🔧 Tools**: Pickaxes, shovels, buckets
- **🧪 Consumables**: Potions, golden apples
- **🍖 Food**: Various food items
- **✨ Special**: Elytra, ender pearls, emeralds

## 🌟 Advanced Features

### Spawn Placement System
- **Dynamic Placement Range**: Automatically adjusts based on total players
  - 2 players: Max 500m
  - 20 players: Max 2000m
  - 20+ players: Additional 500m per 20 players
- **Enemy Distance**: Minimum distance between enemy teams (default: 500m)
- **Team Ratio Adjustments**:
  - Minority team: Placed close together
  - Majority team: Spread apart based on team ratio
- **Safe Placement**: Avoids dangerous locations, places on ground

### Proximity Warning System
- **Red Alert**: Hunter within 1 chunk
- **Orange Alert**: Hunter within 2 chunks  
- **Yellow Alert**: Hunter within 3 chunks

### Compass Tracking System
- Physical compass required for tracking
- Target switching with right-click
- Distance and direction indicators
- Cross-dimensional tracking support

### Team Coordination
- Private team chat with `/r <message>`
- Coordinate sharing with `/pos`
- Real-time teammate positions in Tab list
- Buddy system for enhanced teamwork

### Buddy System
- Partner with one teammate for closer coordination
- Real-time buddy location display in scoreboard
- Orange color highlighting in player list
- Commands: `/buddy invite`, `accept`, `decline`, `remove`, `status`
- Mutual consent required for buddy relationships
- Automatic cleanup on game end or player disconnect

## 🔧 Technical Details

- **Language**: Kotlin 1.9.24
- **Build Tool**: Gradle 8.8
- **Target JVM**: Java 21
- **API**: Spigot/Paper 1.21.4
- **Architecture**: Event-driven with comprehensive manager classes

### Building

1. Clone the repository
2. Run `./gradlew build`
3. The plugin JAR will be in `build/libs/`

### Message Key Validation

The build process automatically checks that all message keys used in the code are defined in both `ja.yml` and `en.yml` files. 

To manually check message keys:
```bash
./gradlew checkMessageKeys
```

If any keys are missing, the build will fail and provide suggestions for the missing keys.

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

## 🤝 Contributing

We welcome contributions! Please feel free to:
- Report bugs through GitHub Issues
- Suggest new features
- Submit pull requests
- Help with translations

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔗 Links

- [Official Guide](https://0x48lab.github.io/minecraft_manhunt/)
- [Modrinth Page](https://modrinth.com/plugin/man-hunt-latest)
- [GitHub Repository](https://github.com/0x48lab/minecraft_manhunt)
- [Issue Tracker](https://github.com/0x48lab/minecraft_manhunt/issues)

---

**🎮 Ready to start your Manhunt adventure? Download now and experience the ultimate chase!**