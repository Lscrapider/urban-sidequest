package com.urbansidequest.backend.handler.route.linear;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import org.springframework.stereotype.Component;

/**
 * Linear Ranker 打分：M_final = M_base + ΣDelta，输出 8 个可解释子分数与 clamped linearScore。
 *
 * <p>所有 baseWeight 与 Delta 数值对应设计文档《POI线性打分矩阵取值设计》§6.2 / §7，是 v1 人工初值，
 * 待真实数据回归后微调。transit mask 与 W_budget 门控已在 {@link PoiLinearFeatureExtractor} 应用，
 * 此处仅做权重叠加。</p>
 */
@Component
public class PoiLinearScorer {

    // —— W_interest ——
    private static final double W_INTEREST_MATCH = 0.25d;

    // —— W_goal base ——
    private static final double W_GOAL_CLASSIC = 0.06d;
    private static final double W_GOAL_LOCAL = 0.05d;
    private static final double W_GOAL_PHOTO = 0.04d;
    private static final double W_GOAL_NIGHT_FRIENDLY = 0.03d;
    private static final double W_GOAL_QUIET = 0.02d;
    private static final double W_GOAL_HIDDEN = 0.03d;
    private static final double W_GOAL_NIGHT_MATCH = 0.05d;
    private static final double W_GOAL_MEAL_MATCH = 0.05d;

    // —— W_quality ——
    private static final double W_QUALITY_RATING = 0.20d;
    private static final double W_QUALITY_HAS_IMAGE = 0.05d;
    private static final double W_QUALITY_RATING_MISSING = -0.03d;

    // —— W_transport base ——
    private static final double W_TRANSIT_HIGH = 0.10d;
    private static final double W_TRANSIT_MEDIUM = 0.05d;
    private static final double W_TRANSIT_LOW = -0.03d;
    private static final double W_TRANSIT_DIST = -0.05d;
    private static final double W_WALK_ACCESS = 0.06d;
    private static final double W_RAIN_TRANSPORT_RISK = -0.08d;

    // —— W_distance base ——
    private static final double W_DISTANCE_NORM = -0.20d;
    private static final double W_ISOLATED = -0.08d;
    private static final double W_CLUSTER = 0.03d;
    private static final double W_FATIGUE = -0.06d;
    private static final double W_HEAT_FATIGUE = -0.05d;

    // —— W_budget base ——
    private static final double W_AVG_PRICE = -0.10d;
    private static final double W_PRICE_MISSING = -0.01d;
    private static final double W_FREE = 0.04d;
    private static final double W_EXPENSIVE = -0.10d;

    // —— W_risk ——
    private static final double W_CLOSE_RISK = -0.20d;
    private static final double W_WEATHER_OUTDOOR = -0.10d;
    private static final double W_CATEGORY_DUP = -0.08d;
    private static final double W_MISSING_INFO = -0.12d;

    // —— W_personalization ——
    private static final double W_USER_AFFINITY = 0.15d;
    private static final double W_DIST_PRESSURE = -0.05d;
    private static final double W_BUDGET_PRESSURE = -0.05d;
    private static final double W_TRANSIT_PRESSURE = -0.03d;
    private static final double W_EXPLORATION = 0.05d;

    public PoiLinearScoreDTO score(PoiCandidateDTO candidate, PoiLinearFeatures x, LinearScoringContext env) {
        RouteGoal goal = env.routeGoal();
        TransportProfile transport = env.transportProfile();
        BudgetLevel budget = env.budgetLevel();
        boolean night = env.routeTimeStructure() == RouteTimeStructure.NIGHT;

        double interestScore = W_INTEREST_MATCH * x.interestMatchRatio();

        double goalScore =
                (W_GOAL_CLASSIC + goalDelta(goal, "classic")) * x.isClassic()
                + (W_GOAL_LOCAL + goalDelta(goal, "local")) * x.isLocal()
                + (W_GOAL_PHOTO + goalDelta(goal, "photo")) * x.isPhotoFriendly()
                + (W_GOAL_NIGHT_FRIENDLY + goalDelta(goal, "nightFriendly")) * x.isNightFriendly()
                + (W_GOAL_QUIET + goalDelta(goal, "quiet")) * x.isQuiet()
                + (W_GOAL_HIDDEN + goalDelta(goal, "hidden")) * x.isHiddenGem()
                + W_GOAL_NIGHT_MATCH * x.nightMatch()
                + W_GOAL_MEAL_MATCH * x.mealMatch();

        double qualityScore =
                W_QUALITY_RATING * x.ratingNorm()
                + W_QUALITY_HAS_IMAGE * x.hasImage()
                + W_QUALITY_RATING_MISSING * x.isRatingMissing();

        double transportScore =
                (W_TRANSIT_HIGH + transportDelta(transport, "high")) * x.transitHigh()
                + W_TRANSIT_MEDIUM * x.transitMedium()
                + (W_TRANSIT_LOW + transportDelta(transport, "low")) * x.transitLow()
                + (W_TRANSIT_DIST + transportDelta(transport, "transitDist") + (night ? -0.03d : 0d)) * x.nearestTransitDistanceNorm()
                + (W_WALK_ACCESS + transportDelta(transport, "walkAcc")) * x.walkingAccessibility()
                + W_RAIN_TRANSPORT_RISK * x.rainTransportRisk();

        double distanceCost =
                (W_DISTANCE_NORM + transportDelta(transport, "distanceNorm") + (night ? -0.04d : 0d)) * x.distanceNorm()
                + (W_ISOLATED + transportDelta(transport, "isolated") + (night ? -0.03d : 0d)) * x.isolatedDistanceNorm()
                + (W_CLUSTER + transportDelta(transport, "cluster")) * x.clusterConnectivity()
                + (W_FATIGUE + transportDelta(transport, "fatigue")) * x.distanceFatiguePressure()
                + W_HEAT_FATIGUE * x.heatFatigueRisk();

        double budgetCost =
                (W_AVG_PRICE + budgetDelta(budget, "price")) * x.avgPriceNorm()
                + W_PRICE_MISSING * x.isPriceMissing()
                + (W_FREE + budgetDelta(budget, "free")) * x.isFree()
                + (W_EXPENSIVE + budgetDelta(budget, "expensive")) * x.expensivePoiRisk();

        double riskCost =
                W_CLOSE_RISK * x.closeRisk()
                + W_WEATHER_OUTDOOR * x.weatherOutdoorRisk()
                + W_CATEGORY_DUP * x.categoryDuplicateRisk()
                + W_MISSING_INFO * x.missingInfoRisk();

        double personalizationScore =
                W_USER_AFFINITY * x.userInterestAffinity()
                + W_DIST_PRESSURE * x.personalizedDistancePressure()
                + W_BUDGET_PRESSURE * x.personalizedBudgetPressure()
                + W_TRANSIT_PRESSURE * x.personalizedTransitPressure()
                + W_EXPLORATION * x.personalizedExplorationMatch();

        double total = interestScore + goalScore + qualityScore + transportScore
                + distanceCost + budgetCost + riskCost + personalizationScore;
        double linearScore = LinearScoreConstants.clampScore(total);

        return new PoiLinearScoreDTO(
                candidate.poiId(),
                candidate.name(),
                interestScore,
                goalScore,
                qualityScore,
                transportScore,
                distanceCost,
                budgetCost,
                riskCost,
                personalizationScore,
                x.distanceMeters(),
                x.effectiveRadiusMeters(),
                x.distanceNorm(),
                x.isolatedDistanceNorm(),
                x.clusterConnectivity(),
                x.distanceFatiguePressure(),
                linearScore
        );
    }

    // —— Delta_goal（§7.1）：放大命中语义；LOW_BUDGET v1 no-op ——
    private static double goalDelta(RouteGoal goal, String target) {
        if (goal == null) {
            return 0d;
        }
        return switch (goal) {
            case STEADY -> switch (target) {
                case "quiet" -> 0.04d;
                case "hidden" -> 0.01d;
                default -> 0d;
            };
            case CLASSIC -> switch (target) {
                case "classic" -> 0.06d;
                case "hidden" -> -0.02d;
                default -> 0d;
            };
            case LOCAL -> switch (target) {
                case "local" -> 0.06d;
                case "hidden" -> 0.02d;
                case "classic" -> -0.02d;
                default -> 0d;
            };
            case NIGHT -> "nightFriendly".equals(target) ? 0.05d : 0d;
            case PHOTO -> switch (target) {
                case "photo" -> 0.06d;
                case "classic" -> 0.02d;
                default -> 0d;
            };
            case LOW_BUDGET -> 0d;
        };
    }

    // —— Delta_transport（§7.2）：调 W_transport + W_distance 敏感度 ——
    private static double transportDelta(TransportProfile transport, String target) {
        if (transport == null) {
            return 0d;
        }
        return switch (transport) {
            case WALK_ONLY -> switch (target) {
                case "high" -> -0.05d;
                case "low" -> 0.03d;
                case "transitDist" -> 0.05d;
                case "walkAcc" -> 0.03d;
                case "distanceNorm" -> -0.06d;
                case "isolated" -> -0.03d;
                case "fatigue" -> -0.03d;
                case "cluster" -> 0.02d;
                default -> 0d;
            };
            case WALK_SUBWAY -> switch (target) {
                case "high" -> 0.03d;
                case "transitDist" -> -0.02d;
                case "distanceNorm" -> 0.02d;
                default -> 0d;
            };
            case WALK_BUS -> switch (target) {
                case "transitDist" -> -0.01d;
                case "distanceNorm" -> 0.01d;
                case "cluster" -> 0.01d;
                default -> 0d;
            };
            case WALK_TRANSIT -> switch (target) {
                case "high" -> 0.01d;
                case "transitDist" -> -0.01d;
                case "distanceNorm" -> 0.01d;
                default -> 0d;
            };
            case BIKE_SUBWAY -> switch (target) {
                case "high" -> 0.02d;
                case "distanceNorm" -> 0.04d;
                case "fatigue" -> 0.02d;
                case "cluster" -> -0.01d;
                default -> 0d;
            };
            case WALK_TAXI -> switch (target) {
                case "high" -> -0.03d;
                case "low" -> 0.03d;
                case "transitDist" -> 0.05d;
                case "distanceNorm" -> 0.05d;
                case "isolated" -> 0.04d;
                case "fatigue" -> 0.03d;
                case "cluster" -> -0.02d;
                default -> 0d;
            };
        };
    }

    // —— Delta_budget（§7.3，v1 budgetLevel 默认 NORMAL no-op）——
    private static double budgetDelta(BudgetLevel budget, String target) {
        if (budget == null) {
            return 0d;
        }
        return switch (budget) {
            case LOW -> switch (target) {
                case "price" -> -0.06d;
                case "expensive" -> -0.06d;
                case "free" -> 0.04d;
                default -> 0d;
            };
            case FLEXIBLE -> switch (target) {
                case "price" -> 0.04d;
                case "expensive" -> 0.04d;
                default -> 0d;
            };
            case NORMAL -> 0d;
        };
    }
}
