package com.hacklab.manhunt.economy

/**
 * 移動報酬に関する設定値
 */
data class MovementConfig(
    // スプリント移動報酬（1ブロックあたり）
    val sprintRewardPerBlock: Double = 0.2,
    
    // 1分間の最大スプリント報酬
    val sprintMaxRewardPerMinute: Int = 50,
    
    // スプリント報酬のクールダウン（秒）
    val sprintRewardCooldown: Int = 1
) {
    companion object {
        fun fromConfigMap(map: Map<String, Any>): MovementConfig {
            return MovementConfig(
                sprintRewardPerBlock = (map["sprint-reward-per-block"] as? Double) ?: 0.2,
                sprintMaxRewardPerMinute = (map["sprint-max-reward-per-minute"] as? Int) ?: 50,
                sprintRewardCooldown = (map["sprint-reward-cooldown"] as? Int) ?: 1
            )
        }
    }
}