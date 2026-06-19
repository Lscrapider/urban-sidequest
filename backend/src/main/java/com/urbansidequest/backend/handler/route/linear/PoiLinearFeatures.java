package com.urbansidequest.backend.handler.route.linear;

/**
 * 单个 POI 规约化后的特征向量 X（已应用 transit mask 与 W_budget 消费类门控）。
 *
 * <p>布尔特征用 0/1 的 double 表示，便于直接与权重相乘。仅含"真正进 X"的 poiFeature + derivedFeature；
 * 身份标识（mustVisit/role）不在此（Gate metadata，不进 X）。</p>
 */
public record PoiLinearFeatures(
        // W_interest
        double interestMatchRatio,
        // W_goal（语义 bool + 时间窗 cross）
        double isClassic,
        double isLocal,
        double isPhotoFriendly,
        double isNightFriendly,
        double isQuiet,
        double isHiddenGem,
        double nightMatch,
        double mealMatch,
        // W_quality
        double ratingNorm,
        double hasImage,
        double isRatingMissing,
        // W_transport（transit mask 生效时这些已被置 0）
        double transitHigh,
        double transitMedium,
        double transitLow,
        double nearestTransitDistanceNorm,
        double walkingAccessibility,
        double rainTransportRisk,
        // W_distance
        double distanceNorm,
        double isolatedDistanceNorm,
        double clusterConnectivity,
        double distanceFatiguePressure,
        double heatFatigueRisk,
        // W_budget（非消费类门控生效时这些已被置 0）
        double avgPriceNorm,
        double isPriceMissing,
        double isFree,
        double expensivePoiRisk,
        // W_risk
        double closeRisk,
        double weatherOutdoorRisk,
        double categoryDuplicateRisk,
        double missingInfoRisk,
        // W_personalization（已乘 profileConfidence；personalizedTransitPressure 受 transit mask）
        double userInterestAffinity,
        double personalizedDistancePressure,
        double personalizedBudgetPressure,
        double personalizedTransitPressure,
        double personalizedExplorationMatch
) {
}
