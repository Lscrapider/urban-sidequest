package com.urbansidequest.backend.handler.route.linear;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
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

    private final RouteScoringProperties routeScoringProperties;

    public PoiLinearScorer(RouteScoringProperties routeScoringProperties) {
        this.routeScoringProperties = routeScoringProperties;
    }

    public PoiLinearScoreDTO score(PoiCandidateDTO candidate, PoiLinearFeatures x, LinearScoringContext env) {
        RouteGoal goal = env.routeGoal();
        TransportProfile transport = env.transportProfile();
        BudgetLevel budget = env.budgetLevel();
        boolean night = env.routeTimeStructure() == RouteTimeStructure.NIGHT;

        double interestScore = this.weight("interest-match") * x.interestMatchRatio();

        double goalScore =
                (this.weight("goal.classic") + this.goalDelta(goal, "classic")) * x.isClassic()
                + (this.weight("goal.local") + this.goalDelta(goal, "local")) * x.isLocal()
                + (this.weight("goal.photo") + this.goalDelta(goal, "photo")) * x.isPhotoFriendly()
                + (this.weight("goal.night-friendly") + this.goalDelta(goal, "nightFriendly")) * x.isNightFriendly()
                + (this.weight("goal.quiet") + this.goalDelta(goal, "quiet")) * x.isQuiet()
                + (this.weight("goal.hidden") + this.goalDelta(goal, "hidden")) * x.isHiddenGem()
                + this.weight("goal.night-match") * x.nightMatch()
                + this.weight("goal.meal-match") * x.mealMatch();

        double qualityScore =
                this.weight("quality.rating") * x.ratingNorm()
                + this.weight("quality.has-image") * x.hasImage()
                + this.weight("quality.rating-missing") * x.isRatingMissing();

        double transportScore =
                (this.weight("transit.high") + this.transportDelta(transport, "high")) * x.transitHigh()
                + this.weight("transit.medium") * x.transitMedium()
                + (this.weight("transit.low") + this.transportDelta(transport, "low")) * x.transitLow()
                + (this.weight("transit.dist") + this.transportDelta(transport, "transitDist")
                + (night ? this.weight("night-delta.nearest-transit-distance-norm") : 0d)) * x.nearestTransitDistanceNorm()
                + (this.weight("transit.walk-access") + this.transportDelta(transport, "walkAcc")) * x.walkingAccessibility()
                + this.weight("transit.rain-risk") * x.rainTransportRisk();

        double distanceCost =
                (this.weight("distance.norm") + this.transportDelta(transport, "distanceNorm")
                + (night ? this.weight("night-delta.distance-norm") : 0d)) * x.distanceNorm()
                + (this.weight("distance.isolated") + this.transportDelta(transport, "isolated")
                + (night ? this.weight("night-delta.isolated") : 0d)) * x.isolatedDistanceNorm()
                + (this.weight("distance.cluster") + this.transportDelta(transport, "cluster")) * x.clusterConnectivity()
                + (this.weight("distance.fatigue") + this.transportDelta(transport, "fatigue")) * x.distanceFatiguePressure()
                + this.weight("distance.heat-fatigue") * x.heatFatigueRisk();

        double budgetCost =
                (this.weight("budget.avg-price") + this.budgetDelta(budget, "price")) * x.avgPriceNorm()
                + this.weight("budget.price-missing") * x.isPriceMissing()
                + (this.weight("budget.free") + this.budgetDelta(budget, "free")) * x.isFree()
                + (this.weight("budget.expensive") + this.budgetDelta(budget, "expensive")) * x.expensivePoiRisk();

        double riskCost =
                this.weight("risk.close") * x.closeRisk()
                + this.weight("risk.weather-outdoor") * x.weatherOutdoorRisk()
                + this.weight("risk.category-dup") * x.categoryDuplicateRisk()
                + this.weight("risk.missing-info") * x.missingInfoRisk();

        double personalizationScore =
                this.weight("personalization.user-affinity") * x.userInterestAffinity()
                + this.weight("personalization.dist-pressure") * x.personalizedDistancePressure()
                + this.weight("personalization.budget-pressure") * x.personalizedBudgetPressure()
                + this.weight("personalization.transit-pressure") * x.personalizedTransitPressure()
                + this.weight("personalization.exploration") * x.personalizedExplorationMatch();

        double total = interestScore + goalScore + qualityScore + transportScore
                + distanceCost + budgetCost + riskCost + personalizationScore;
        double linearScore = this.routeScoringProperties.clampScore(total);

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
    private double goalDelta(RouteGoal goal, String target) {
        if (goal == null) {
            return 0d;
        }
        return this.routeScoringProperties.goalDelta(goal, target);
    }

    // —— Delta_transport（§7.2）：只调 W_distance 距离惩罚敏感度 ——
    private double transportDelta(TransportProfile transport, String target) {
        if (transport == null) {
            return 0d;
        }
        return this.routeScoringProperties.transportDelta(transport, target);
    }

    // —— Delta_budget（§7.3，v1 budgetLevel 默认 NORMAL no-op）——
    private double budgetDelta(BudgetLevel budget, String target) {
        if (budget == null) {
            return 0d;
        }
        return this.routeScoringProperties.budgetDelta(budget, target);
    }

    private double weight(String path) {
        return this.routeScoringProperties.linearWeight(path);
    }
}
