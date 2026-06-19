package com.urbansidequest.backend.handler.route.linear;

import java.util.Set;

/**
 * Linear Ranker v1 的规约化参考尺度与缺失默认值（对应设计文档《POI线性打分矩阵取值设计》§3.2）。
 *
 * <p>所有"特征规约化"用到的常量集中在此，便于按真实数据回归校准；打分权重见 {@link PoiLinearScorer}。</p>
 */
public final class LinearScoreConstants {

    private LinearScoreConstants() {
    }

    // —— 规约化参考尺度（§3.2）——
    /** avgPriceNorm 分母：人均预算上限，单位"分"（150 元）。v1 全局常量，不做城市价差。 */
    public static final double BUDGET_CAP_CENT = 15000d;

    /** nearestTransitDistanceNorm 分母，对齐交通档阈值。 */
    public static final double TRANSIT_REF_METERS = 800d;

    /** walkingAccessibility 步行舒适上限。 */
    public static final double WALK_REF_METERS = 1000d;

    /** clusterConnectivity 邻域半径。 */
    public static final double NEIGHBOR_RADIUS_METERS = 300d;

    /** clusterConnectivity 饱和邻域数。 */
    public static final double CONNECT_FULL = 5d;

    /** categoryDuplicateRisk 饱和同类数。 */
    public static final double DUP_FULL = 5d;

    // —— overflow 上限（§3.0）——
    public static final double DISTANCE_NORM_CAP = 1.5d;

    public static final double PRICE_NORM_CAP = 2.0d;

    public static final double TRANSIT_DIST_CAP = 1.5d;

    // —— 缺失默认（§3.1 中性先验）——
    public static final double RATING_MISSING_DEFAULT = 0.5d;

    public static final double PRICE_MISSING_DEFAULT = 0.5d;

    public static final double CLOSE_RISK_MISSING_DEFAULT = 0.2d;

    /** ratingNorm 分母：高德评分满分。 */
    public static final double RATING_FULL = 5d;

    // —— W_budget 门控的消费类大类组（非消费类整组 0，避免误判景点预算）——
    public static final Set<String> CONSUMABLE_CATEGORY_GROUPS = Set.of("FOOD", "REST", "SHOPPING", "NIGHT");

    // —— linearScore 安全 clamp（§8）——
    public static final double SCORE_MIN = -1.0d;

    public static final double SCORE_MAX = 1.0d;

    public static double clampScore(double score) {
        return Math.max(SCORE_MIN, Math.min(SCORE_MAX, score));
    }

    public static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
