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

    /** 段级交通方式预选择：短段直接步行。 */
    public static final int SHORT_WALK_SEGMENT_METERS = 900;

    /** 段级交通方式预选择：地铁起乘直线距离门槛。 */
    public static final int SUBWAY_MIN_SEGMENT_METERS = 1800;

    /** 段级交通方式预选择：中距离段上限，供 WALK_TRANSIT 与 BIKE_SUBWAY 共用。 */
    public static final int MID_DISTANCE_SEGMENT_METERS = 2500;

    /** 中距离公共交通接驳门控：POI 到最近公交/地铁设施的最大距离。 */
    public static final int TRANSIT_ACCESS_MAX_METERS = 800;

    /** 高德步行兜底失败后允许本地直线估算的最大段距离。 */
    public static final int MAX_WALK_FALLBACK_METERS = 2500;

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

    // —— W_budget 门控的消费类内部业态组（非消费类整组 0，避免误判景点预算）——
    public static final Set<String> CONSUMABLE_CATEGORY_GROUPS = Set.of("FOOD", "DRINK", "SHOPPING", "ENTERTAINMENT");

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
