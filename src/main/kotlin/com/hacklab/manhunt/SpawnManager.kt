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
     * ランナーを地上に配置（最適化版）
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
        
        // 事前に候補位置を生成（並列処理可能）
        val candidateLocations = mutableListOf<Location>()
        val numCandidates = runners.size * 5 // 各プレイヤーに対して5つの候補
        
        // 候補位置を事前計算
        for (i in 0 until numCandidates) {
            val distance = Random.nextDouble(minRadius, maxRadius)
            val angle = Random.nextDouble(360.0)
            
            val x = center.x + distance * cos(Math.toRadians(angle))
            val z = center.z + distance * sin(Math.toRadians(angle))
            
            val location = getGroundLocation(world, x, z)
            if (isSafeLocation(location)) {
                candidateLocations.add(location)
            }
        }
        
        // 各ランナーに最適な位置を割り当て
        runners.forEach { runner ->
            var bestLocation: Location? = null
            var bestScore = -1.0
            
            // 候補位置から最適なものを選択
            candidateLocations.forEach { candidate ->
                if (locations.values.any { it.distance(candidate) < 50.0 }) {
                    return@forEach // 近すぎる場合はスキップ
                }
                
                val score = evaluateLocation(candidate, locations.values.toList(), runnerSpread)
                if (score > bestScore) {
                    bestScore = score
                    bestLocation = candidate
                }
            }
            
            // 最適な位置が見つからない場合は新しく生成
            if (bestLocation == null) {
                var attempts = 0
                val maxAttempts = 20 // 試行回数を減らす
                
                do {
                    val distance = Random.nextDouble(minRadius, maxRadius)
                    val angle = Random.nextDouble(360.0)
                    
                    val x = center.x + distance * cos(Math.toRadians(angle))
                    val z = center.z + distance * sin(Math.toRadians(angle))
                    
                    bestLocation = getGroundLocation(world, x, z)
                    attempts++
                } while (attempts < maxAttempts && !isValidRunnerLocation(bestLocation!!, locations.values.toList(), runnerSpread))
            }
            
            val finalLocation = bestLocation ?: world.spawnLocation
            locations[runner] = finalLocation
            candidateLocations.remove(finalLocation) // 使用済みの候補を削除
            
            plugin.logger.info("Runner ${runner.name} placed at: ${finalLocation.blockX}, ${finalLocation.blockY}, ${finalLocation.blockZ}")
        }
        
        return locations
    }
    
    /**
     * 位置の評価スコアを計算
     */
    private fun evaluateLocation(
        location: Location,
        existingLocations: List<Location>,
        targetSpread: Double
    ): Double {
        if (!isSafeLocation(location)) return -1.0
        
        var score = 100.0
        
        // 既存位置との距離を評価
        existingLocations.forEach { existing ->
            val distance = location.distance(existing)
            
            if (targetSpread > 0) {
                // 目標距離に近いほど高スコア
                val diff = kotlin.math.abs(distance - targetSpread)
                score -= diff * 0.1
            } else {
                // 近いほど高スコア（ただし最小距離は保つ）
                if (distance < 50.0) {
                    score -= 50.0
                } else {
                    score += 10.0 / distance
                }
            }
        }
        
        return score
    }
    
    /**
     * ハンターを配置（最適化版）
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
        
        // ランナーから十分離れた候補位置を事前計算
        val candidateLocations = mutableListOf<Location>()
        val numCandidates = hunters.size * 8 // 各プレイヤーに対して8つの候補
        
        for (i in 0 until numCandidates) {
            val distance = Random.nextDouble(minRadius, maxRadius)
            val angle = Random.nextDouble(360.0)
            
            val x = center.x + distance * cos(Math.toRadians(angle))
            val z = center.z + distance * sin(Math.toRadians(angle))
            
            val location = getGroundLocation(world, x, z)
            
            // ランナーから十分離れているかチェック
            if (isSafeLocation(location) && 
                runnerLocations.all { it.distance(location) >= minEnemyDistance }) {
                candidateLocations.add(location)
            }
        }
        
        // 各ハンターに最適な位置を割り当て
        hunters.forEach { hunter ->
            var bestLocation: Location? = null
            var bestScore = -1.0
            
            // 候補位置から最適なものを選択
            candidateLocations.forEach { candidate ->
                if (locations.values.any { it.distance(candidate) < 50.0 }) {
                    return@forEach // 近すぎる場合はスキップ
                }
                
                val score = evaluateHunterLocation(
                    candidate, 
                    locations.values.toList(), 
                    runnerLocations,
                    hunterSpread,
                    minEnemyDistance
                )
                
                if (score > bestScore) {
                    bestScore = score
                    bestLocation = candidate
                }
            }
            
            // 最適な位置が見つからない場合は新しく生成（試行回数削減）
            if (bestLocation == null) {
                var attempts = 0
                val maxAttempts = 30 // 試行回数を減らす
                
                do {
                    val distance = Random.nextDouble(minRadius, maxRadius)
                    val angle = Random.nextDouble(360.0)
                    
                    val x = center.x + distance * cos(Math.toRadians(angle))
                    val z = center.z + distance * sin(Math.toRadians(angle))
                    
                    bestLocation = getGroundLocation(world, x, z)
                    attempts++
                } while (attempts < maxAttempts && 
                         !isValidHunterLocation(bestLocation!!, locations.values.toList(), 
                                              runnerLocations, hunterSpread, minEnemyDistance))
            }
            
            val finalLocation = bestLocation ?: world.spawnLocation
            locations[hunter] = finalLocation
            candidateLocations.remove(finalLocation) // 使用済みの候補を削除
            
            plugin.logger.info("Hunter ${hunter.name} placed at: ${finalLocation.blockX}, ${finalLocation.blockY}, ${finalLocation.blockZ}")
        }
        
        return locations
    }
    
    /**
     * ハンター位置の評価スコアを計算
     */
    private fun evaluateHunterLocation(
        location: Location,
        existingHunters: List<Location>,
        runnerLocations: List<Location>,
        targetSpread: Double,
        minEnemyDistance: Double
    ): Double {
        if (!isSafeLocation(location)) return -1.0
        
        // ランナーとの最小距離チェック
        val minRunnerDist = runnerLocations.minOfOrNull { it.distance(location) } ?: Double.MAX_VALUE
        if (minRunnerDist < minEnemyDistance) return -1.0
        
        var score = 100.0
        
        // ランナーからの距離ボーナス（遠すぎず近すぎず）
        val idealEnemyDistance = minEnemyDistance * 1.5
        val enemyDistDiff = kotlin.math.abs(minRunnerDist - idealEnemyDistance)
        score -= enemyDistDiff * 0.05
        
        // 既存ハンターとの距離を評価
        existingHunters.forEach { existing ->
            val distance = location.distance(existing)
            
            if (targetSpread > 0) {
                // 目標距離に近いほど高スコア
                val diff = kotlin.math.abs(distance - targetSpread)
                score -= diff * 0.1
            } else {
                // 近いほど高スコア（ただし最小距離は保つ）
                if (distance < 50.0) {
                    score -= 50.0
                } else {
                    score += 10.0 / distance
                }
            }
        }
        
        return score
    }
    
    /**
     * 地上の安全な位置を取得（最適化版）
     */
    private fun getGroundLocation(world: World, x: Double, z: Double): Location {
        val xInt = x.toInt()
        val zInt = z.toInt()
        
        // チャンクがロードされていない場合は先にロード
        val chunk = world.getChunkAt(xInt shr 4, zInt shr 4)
        if (!chunk.isLoaded) {
            chunk.load()
        }
        
        // 最高ブロックから開始
        val highestY = world.getHighestBlockYAt(xInt, zInt)
        
        // 最高点が十分低い場合は簡易チェック
        if (highestY < 100) {
            val highestBlock = world.getBlockAt(xInt, highestY, zInt)
            if (highestBlock.type.isSolid && !isUnsafeBlock(highestBlock.type)) {
                return Location(world, x, highestY + 1.0, z)
            }
        }
        
        // 二分探索で効率的に安全な位置を探す
        var minY = 60 // 通常の地表レベル付近から開始
        var maxY = highestY
        var safeY = -1
        
        while (minY <= maxY && safeY == -1) {
            val midY = (minY + maxY) / 2
            val block = world.getBlockAt(xInt, midY, zInt)
            val below = world.getBlockAt(xInt, midY - 1, zInt)
            val above = world.getBlockAt(xInt, midY + 1, zInt)
            
            if (below.type.isSolid && !isUnsafeBlock(below.type) &&
                block.type == Material.AIR && 
                above.type == Material.AIR) {
                safeY = midY
                // より高い安全な位置があるか確認
                for (y in midY + 1..minOf(midY + 5, maxY)) {
                    val checkBelow = world.getBlockAt(xInt, y - 1, zInt)
                    val checkBlock = world.getBlockAt(xInt, y, zInt)
                    val checkAbove = world.getBlockAt(xInt, y + 1, zInt)
                    
                    if (checkBelow.type.isSolid && !isUnsafeBlock(checkBelow.type) &&
                        checkBlock.type == Material.AIR && 
                        checkAbove.type == Material.AIR) {
                        safeY = y
                    } else {
                        break
                    }
                }
            } else if (!below.type.isSolid || below.type == Material.AIR) {
                minY = midY + 1
            } else {
                maxY = midY - 1
            }
        }
        
        // 安全な位置が見つかった場合
        if (safeY != -1) {
            return Location(world, x, safeY.toDouble(), z)
        }
        
        // 見つからない場合は最高点を使用
        return Location(world, x, highestY + 1.0, z)
    }
    
    /**
     * 危険なブロックタイプかチェック
     */
    private fun isUnsafeBlock(type: Material): Boolean {
        return type == Material.LAVA || 
               type == Material.WATER || 
               type == Material.CACTUS ||
               type == Material.MAGMA_BLOCK ||
               type.name.contains("FIRE")
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