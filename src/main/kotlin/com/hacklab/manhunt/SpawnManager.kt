package com.hacklab.manhunt

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * プレイヤーのスポーン位置を管理するクラス
 * チーム人数比に応じて配置距離を調整
 */
class SpawnManager(
    private val plugin: Main,
    private val gameManager: GameManager,
    private val configManager: ConfigManager
) {
    
    /**
     * 全プレイヤーのスポーン位置を生成
     */
    fun generateSpawnLocations(world: World): Map<Player, Location> {
        val center = world.spawnLocation
        val hunters = gameManager.getAllHunters()
        val runners = gameManager.getAllRunners()
        val locations = mutableMapOf<Player, Location>()
        
        // プレイヤー総数に基づいて最大半径を動的に計算
        val totalPlayers = hunters.size + runners.size
        val dynamicMaxRadius = calculateDynamicRadius(totalPlayers)
        
        // 人数比を計算
        val hunterRatio = if (runners.isEmpty()) 1.0 else hunters.size.toDouble() / runners.size.toDouble()
        val runnerRatio = if (hunters.isEmpty()) 1.0 else runners.size.toDouble() / hunters.size.toDouble()
        
        plugin.logger.info("=== Spawn Generation Started ===")
        plugin.logger.info("Total players: $totalPlayers")
        plugin.logger.info("Dynamic max radius: ${dynamicMaxRadius.toInt()}m (base: ${configManager.getSpawnMaxRadius().toInt()}m)")
        plugin.logger.info("Team ratio - Hunters: ${hunters.size}, Runners: ${runners.size}")
        plugin.logger.info("Hunter ratio: $hunterRatio, Runner ratio: $runnerRatio")
        
        // Step 1: ランナーを先に配置（地上）
        plugin.logger.info("\nPlacing Runners first (ground level)...")
        val runnerLocations = placeRunnersOnGround(world, center, runners, runnerRatio, dynamicMaxRadius)
        locations.putAll(runnerLocations)
        
        // Step 2: ハンターを配置（ランナーから最低500m離して）
        plugin.logger.info("\nPlacing Hunters (min ${configManager.getSpawnEnemyMinDistance()}m from runners)...")
        val hunterLocations = placeHunters(world, center, hunters, runnerLocations.values.toList(), hunterRatio, dynamicMaxRadius)
        locations.putAll(hunterLocations)
        
        // 統計情報を出力
        logSpawnStatistics(locations, hunters, runners)
        
        return locations
    }
    
    /**
     * プレイヤー総数に基づいて動的な最大半径を計算
     * 2人: 500m
     * 20人: 2000m
     * 20人以上: さらに増加
     */
    private fun calculateDynamicRadius(totalPlayers: Int): Double {
        return when {
            totalPlayers <= 2 -> 500.0
            totalPlayers <= 20 -> {
                // 2-20人の間で線形補間 (500-2000)
                val ratio = (totalPlayers - 2) / 18.0
                500.0 + (1500.0 * ratio)
            }
            else -> {
                // 20人以上: 20人ごとに500m追加
                val extraPlayers = totalPlayers - 20
                val extraRadius = (extraPlayers / 20.0) * 500.0
                2000.0 + extraRadius
            }
        }
    }
    
    /**
     * ランナーを地上に配置
     */
    private fun placeRunnersOnGround(
        world: World, 
        center: Location, 
        runners: List<Player>,
        runnerRatio: Double,
        maxRadius: Double
    ): Map<Player, Location> {
        val locations = mutableMapOf<Player, Location>()
        val minRadius = configManager.getSpawnMinRadius()
        
        // ランナーが多数派の場合の分散度
        val runnerSpread = calculateTeamSpread(runnerRatio)
        if (runnerSpread > 0) {
            plugin.logger.info("Runners will be spread up to ${runnerSpread}m apart (majority team)")
        } else {
            plugin.logger.info("Runners will be placed close together (minority team)")
        }
        
        runners.forEachIndexed { index, runner ->
            var location: Location
            var attempts = 0
            val maxAttempts = 100
            
            do {
                // ランダムな位置を生成
                val distance = Random.nextDouble(minRadius, maxRadius)
                val angle = Random.nextDouble(360.0)
                
                val x = center.x + distance * cos(Math.toRadians(angle))
                val z = center.z + distance * sin(Math.toRadians(angle))
                
                // 地上の高さを取得
                location = getGroundLocation(world, x, z)
                
                attempts++
                if (attempts >= maxAttempts) {
                    plugin.logger.warning("Failed to find valid location for runner ${runner.name} after $maxAttempts attempts")
                    break
                }
            } while (!isValidRunnerLocation(location, locations.values.toList(), runnerSpread))
            
            locations[runner] = location
            plugin.logger.info("Runner ${runner.name} placed at: ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
        
        return locations
    }
    
    /**
     * ハンターを配置
     */
    private fun placeHunters(
        world: World,
        center: Location,
        hunters: List<Player>,
        runnerLocations: List<Location>,
        hunterRatio: Double,
        maxRadius: Double
    ): Map<Player, Location> {
        val locations = mutableMapOf<Player, Location>()
        val minRadius = configManager.getSpawnMinRadius()
        val minEnemyDistance = configManager.getSpawnEnemyMinDistance()
        
        // ハンターが多数派の場合の分散度
        val hunterSpread = calculateTeamSpread(hunterRatio)
        if (hunterSpread > 0) {
            plugin.logger.info("Hunters will be spread up to ${hunterSpread}m apart (majority team)")
        } else {
            plugin.logger.info("Hunters will be placed close together (minority team)")
        }
        
        hunters.forEachIndexed { index, hunter ->
            var location: Location
            var attempts = 0
            val maxAttempts = 100
            
            do {
                val distance = Random.nextDouble(minRadius, maxRadius)
                val angle = Random.nextDouble(360.0)
                
                val x = center.x + distance * cos(Math.toRadians(angle))
                val z = center.z + distance * sin(Math.toRadians(angle))
                
                location = getGroundLocation(world, x, z)
                
                attempts++
                if (attempts >= maxAttempts) {
                    plugin.logger.warning("Failed to find valid location for hunter ${hunter.name} after $maxAttempts attempts")
                    break
                }
            } while (!isValidHunterLocation(location, locations.values.toList(), runnerLocations, hunterSpread, minEnemyDistance))
            
            locations[hunter] = location
            plugin.logger.info("Hunter ${hunter.name} placed at: ${location.blockX}, ${location.blockY}, ${location.blockZ}")
        }
        
        return locations
    }
    
    /**
     * 地上の安全な位置を取得
     */
    private fun getGroundLocation(world: World, x: Double, z: Double): Location {
        val highestBlock = world.getHighestBlockAt(x.toInt(), z.toInt())
        var y = highestBlock.y
        
        // 安全な地面を探す（水や溶岩の上でない）
        while (y > 0) {
            val block = world.getBlockAt(x.toInt(), y, z.toInt())
            val below = world.getBlockAt(x.toInt(), y - 1, z.toInt())
            
            if (below.type.isSolid && 
                block.type == Material.AIR && 
                world.getBlockAt(x.toInt(), y + 1, z.toInt()).type == Material.AIR) {
                return Location(world, x, y.toDouble(), z)
            }
            y--
        }
        
        // 見つからない場合は最高点を使用
        return highestBlock.location.add(0.5, 1.0, 0.5)
    }
    
    /**
     * チームの分散度を計算
     */
    private fun calculateTeamSpread(ratio: Double): Double {
        val config = configManager.getSpawnTeamSpreadConfig()
        if (!config.enabled) return 0.0
        
        return when {
            ratio >= config.highThreshold -> config.highDistance
            ratio >= config.mediumThreshold -> config.mediumDistance
            ratio > config.lowThreshold -> config.lowDistance
            else -> 0.0
        }
    }
    
    /**
     * ランナーの位置が有効かチェック
     */
    private fun isValidRunnerLocation(
        location: Location,
        existingRunners: List<Location>,
        minTeamDistance: Double
    ): Boolean {
        // 危険な場所でないか確認
        if (!isSafeLocation(location)) {
            return false
        }
        
        // 他のランナーとの距離をチェック（多数派の場合）
        if (minTeamDistance > 0) {
            for (existing in existingRunners) {
                if (location.distance(existing) < minTeamDistance) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * ハンターの位置が有効かチェック
     */
    private fun isValidHunterLocation(
        location: Location,
        existingHunters: List<Location>,
        runnerLocations: List<Location>,
        minTeamDistance: Double,
        minEnemyDistance: Double
    ): Boolean {
        // 危険な場所でないか確認
        if (!isSafeLocation(location)) {
            return false
        }
        
        // ランナーとの最小距離をチェック
        for (runner in runnerLocations) {
            if (location.distance(runner) < minEnemyDistance) {
                return false
            }
        }
        
        // 他のハンターとの距離をチェック（多数派の場合）
        if (minTeamDistance > 0) {
            for (existing in existingHunters) {
                if (location.distance(existing) < minTeamDistance) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * 安全な位置かチェック
     */
    private fun isSafeLocation(location: Location): Boolean {
        val block = location.block
        val below = location.clone().add(0.0, -1.0, 0.0).block
        
        // 足元が危険でないか
        if (below.type == Material.LAVA || below.type == Material.WATER) {
            return false
        }
        
        // 現在位置が安全か
        if (block.type != Material.AIR && block.type != Material.SHORT_GRASS && 
            block.type != Material.TALL_GRASS && !block.type.name.contains("FLOWER")) {
            return false
        }
        
        return true
    }
    
    /**
     * スポーン統計情報をログ出力
     */
    private fun logSpawnStatistics(
        locations: Map<Player, Location>,
        hunters: List<Player>,
        runners: List<Player>
    ) {
        plugin.logger.info("\n=== Spawn Generation Complete ===")
        
        // 敵同士の最小距離を計算
        var minEnemyDistance = Double.MAX_VALUE
        var maxEnemyDistance = 0.0
        
        for (hunter in hunters) {
            val hunterLoc = locations[hunter] ?: continue
            for (runner in runners) {
                val runnerLoc = locations[runner] ?: continue
                val distance = hunterLoc.distance(runnerLoc)
                if (distance < minEnemyDistance) minEnemyDistance = distance
                if (distance > maxEnemyDistance) maxEnemyDistance = distance
            }
        }
        
        // ハンター同士の最大距離
        var maxHunterDistance = 0.0
        if (hunters.size > 1) {
            for (i in hunters.indices) {
                for (j in i + 1 until hunters.size) {
                    val loc1 = locations[hunters[i]] ?: continue
                    val loc2 = locations[hunters[j]] ?: continue
                    val distance = loc1.distance(loc2)
                    if (distance > maxHunterDistance) maxHunterDistance = distance
                }
            }
        }
        
        // ランナー同士の最大距離
        var maxRunnerDistance = 0.0
        if (runners.size > 1) {
            for (i in runners.indices) {
                for (j in i + 1 until runners.size) {
                    val loc1 = locations[runners[i]] ?: continue
                    val loc2 = locations[runners[j]] ?: continue
                    val distance = loc1.distance(loc2)
                    if (distance > maxRunnerDistance) maxRunnerDistance = distance
                }
            }
        }
        
        plugin.logger.info("Statistics:")
        if (minEnemyDistance != Double.MAX_VALUE) {
            plugin.logger.info("- Min distance between enemies: ${minEnemyDistance.toInt()}m")
            plugin.logger.info("- Max distance between enemies: ${maxEnemyDistance.toInt()}m")
        }
        if (maxHunterDistance > 0) {
            plugin.logger.info("- Max distance between hunters: ${maxHunterDistance.toInt()}m")
        }
        if (maxRunnerDistance > 0) {
            plugin.logger.info("- Max distance between runners: ${maxRunnerDistance.toInt()}m")
        }
        plugin.logger.info("================================\n")
    }
}