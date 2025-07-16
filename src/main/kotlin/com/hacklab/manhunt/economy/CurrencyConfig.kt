package com.hacklab.manhunt.economy

/**
 * 通貨獲得に関する設定値
 */
data class CurrencyConfig(
    // ハンター用設定
    val hunterDamageReward: Int = 55,           // 1ダメージあたりの報酬
    val hunterKillReward: Int = 500,            // キル報酬
    val hunterProximityReward: Int = 100,       // 接近ボーナス
    val hunterProximityDistance: Int = 50,      // 接近ボーナスの距離（メートル）
    val hunterTimeBonus: Double = 0.3,          // 1秒あたりの時間ボーナス
    val hunterTimeBonusInterval: Int = 10,      // 時間ボーナスの支給間隔（秒）
    val hunterTrackingReward: Int = 30,         // 追跡持続ボーナス
    val hunterTrackingDistance: Int = 100,      // 追跡持続ボーナスの距離（メートル）
    val hunterTrackingDuration: Int = 30,       // 追跡持続に必要な時間（秒）
    val hunterTrackingCooldown: Int = 60,       // 追跡持続ボーナスのクールダウン（秒）
    
    // ランナー用設定
    val runnerSurvivalBonus: Double = 0.5,      // 1秒あたりの生存ボーナス
    val runnerSurvivalInterval: Int = 5,        // 生存ボーナスの支給間隔（秒）
    val runnerNetherReward: Int = 1000,         // ネザー到達報酬
    val runnerFortressReward: Int = 1500,       // 要塞発見報酬
    val runnerEndReward: Int = 2000,            // エンド到達報酬
    val runnerDiamondReward: Int = 100,         // ダイヤモンド1個あたりの報酬
    val runnerEscapeReward: Int = 50,           // 逃走成功ボーナス
    val runnerEscapeDistance: Int = 100,        // 逃走成功とみなす距離（メートル）
    val advancementReward: Int = 10,            // 実績解除報酬
    
    // 共通設定
    val startingBalance: Int = 0,               // ゲーム開始時の所持金
    val maxBalance: Int = 999999,               // 最大所持金
    val currencyUnit: String = "g"              // 通貨単位
) {
    companion object {
        fun fromConfigMap(map: Map<String, Any>): CurrencyConfig {
            return CurrencyConfig(
                hunterDamageReward = (map["hunter-damage-reward"] as? Int) ?: 55,
                hunterKillReward = (map["hunter-kill-reward"] as? Int) ?: 500,
                hunterProximityReward = (map["hunter-proximity-reward"] as? Int) ?: 100,
                hunterProximityDistance = (map["hunter-proximity-distance"] as? Int) ?: 50,
                hunterTimeBonus = (map["hunter-time-bonus"] as? Double) ?: 0.3,
                hunterTimeBonusInterval = (map["hunter-time-bonus-interval"] as? Int) ?: 10,
                hunterTrackingReward = (map["hunter-tracking-reward"] as? Int) ?: 30,
                hunterTrackingDistance = (map["hunter-tracking-distance"] as? Int) ?: 100,
                hunterTrackingDuration = (map["hunter-tracking-duration"] as? Int) ?: 30,
                hunterTrackingCooldown = (map["hunter-tracking-cooldown"] as? Int) ?: 60,
                
                runnerSurvivalBonus = (map["runner-survival-bonus"] as? Double) ?: 0.5,
                runnerSurvivalInterval = (map["runner-survival-interval"] as? Int) ?: 5,
                runnerNetherReward = (map["runner-nether-reward"] as? Int) ?: 1000,
                runnerFortressReward = (map["runner-fortress-reward"] as? Int) ?: 1500,
                runnerEndReward = (map["runner-end-reward"] as? Int) ?: 2000,
                runnerDiamondReward = (map["runner-diamond-reward"] as? Int) ?: 100,
                runnerEscapeReward = (map["runner-escape-reward"] as? Int) ?: 50,
                runnerEscapeDistance = (map["runner-escape-distance"] as? Int) ?: 100,
                advancementReward = (map["advancement-reward"] as? Int) ?: 10,
                
                startingBalance = (map["starting-balance"] as? Int) ?: 0,
                maxBalance = (map["max-balance"] as? Int) ?: 999999,
                currencyUnit = (map["currency-unit"] as? String) ?: "g"
            )
        }
    }
}