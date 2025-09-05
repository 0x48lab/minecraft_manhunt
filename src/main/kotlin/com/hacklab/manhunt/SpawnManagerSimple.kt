package com.hacklab.manhunt

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * シンプルなスポーン配置マネージャー
 * プレイヤーを円周上に等間隔で配置
 */
class SpawnManagerSimple(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val configManager: ConfigManager
) {
    
    /**
     * 全プレイヤーのスポーン位置を生成（シンプル版）
     */
    fun generateSpawnLocations(world: World): Map<Player, Location> {
        val center = world.spawnLocation
        val hunters = gameManager.getAllHunters()
        val runners = gameManager.getAllRunners()
        val locations = mutableMapOf<Player, Location>()
        
        // プレイヤー総数に基づいて半径を計算
        val totalPlayers = hunters.size + runners.size
        val radius = calculateRadius(totalPlayers)
        
        plugin.logger.info("=== Simple Spawn Generation ===")
        plugin.logger.info("Total players: $totalPlayers")
        plugin.logger.info("Spawn radius: ${radius.toInt()}m")
        plugin.logger.info("Hunters: ${hunters.size}, Runners: ${runners.size}")
        
        // ランナーを先に配置（完全ランダム）
        runners.forEach { runner ->
            val angle = Math.random() * 2 * PI
            val distance = Math.random() * radius
            val x = center.x + distance * cos(angle)
            val z = center.z + distance * sin(angle)
            val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1
            val location = Location(world, x, y.toDouble(), z)
            
            locations[runner] = location
            plugin.logger.info("Runner ${runner.name} placed at: ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
        
        // ハンターを配置（ランナーから最低50m離す）
        hunters.forEach { hunter ->
            var attempts = 0
            var location: Location? = null
            
            // ランナーから最低50m離れた位置を探す（最大10回試行）
            while (attempts < 10 && location == null) {
                val angle = Math.random() * 2 * PI
                val distance = Math.random() * radius
                val x = center.x + distance * cos(angle)
                val z = center.z + distance * sin(angle)
                val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1
                val testLocation = Location(world, x, y.toDouble(), z)
                
                // ランナーから十分離れているかチェック
                var tooClose = false
                for (runner in runners) {
                    val runnerLoc = locations[runner] ?: continue
                    if (runnerLoc.distance(testLocation) < 50.0) {
                        tooClose = true
                        break
                    }
                }
                
                if (!tooClose) {
                    location = testLocation
                }
                attempts++
            }
            
            // 位置が見つからない場合は強制的に配置
            if (location == null) {
                val angle = Math.random() * 2 * PI
                val distance = Math.random() * radius
                val x = center.x + distance * cos(angle)
                val z = center.z + distance * sin(angle)
                val y = world.getHighestBlockYAt(x.toInt(), z.toInt()) + 1
                location = Location(world, x, y.toDouble(), z)
            }
            
            locations[hunter] = location
            plugin.logger.info("Hunter ${hunter.name} placed at: ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
        
        // 統計情報
        logStatistics(locations, hunters, runners)
        
        return locations
    }
    
    /**
     * プレイヤー数に基づいて半径を計算
     */
    private fun calculateRadius(totalPlayers: Int): Double {
        return when {
            totalPlayers <= 2 -> 300.0
            totalPlayers <= 5 -> 500.0
            totalPlayers <= 10 -> 800.0
            totalPlayers <= 20 -> 1200.0
            else -> 1500.0 + ((totalPlayers - 20) * 30.0) // 20人以上は1人あたり30m追加
        }
    }
    
    /**
     * 統計情報をログ出力
     */
    private fun logStatistics(
        locations: Map<Player, Location>,
        hunters: List<Player>,
        runners: List<Player>
    ) {
        if (hunters.isEmpty() || runners.isEmpty()) {
            plugin.logger.info("=== Spawn Complete ===")
            return
        }
        
        // 敵同士の最小/最大距離を計算
        var minDistance = Double.MAX_VALUE
        var maxDistance = 0.0
        
        for (hunter in hunters) {
            val hunterLoc = locations[hunter] ?: continue
            for (runner in runners) {
                val runnerLoc = locations[runner] ?: continue
                val distance = hunterLoc.distance(runnerLoc)
                if (distance < minDistance) minDistance = distance
                if (distance > maxDistance) maxDistance = distance
            }
        }
        
        plugin.logger.info("=== Spawn Statistics ===")
        if (minDistance != Double.MAX_VALUE) {
            plugin.logger.info("Min distance between enemies: ${minDistance.toInt()}m")
            plugin.logger.info("Max distance between enemies: ${maxDistance.toInt()}m")
        }
        plugin.logger.info("========================")
    }
}