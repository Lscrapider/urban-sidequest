package com.urbansidequest.backend.handler.route.linear;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.TransitFacilityDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 把 POI + 请求/环境/画像派生量规约成 {@link PoiLinearFeatures}。
 *
 * <p>已在此应用 transit mask（transportSignalAvailable=false 时关闭交通相关列）与 W_budget 消费类门控，
 * 使下游 {@link PoiLinearScorer} 只需做权重相乘。</p>
 */
@Component
public class PoiLinearFeatureExtractor {

    private static final String TRANSIT_TYPE_BUS = "BUS";

    private static final String TRANSIT_TYPE_SUBWAY = "SUBWAY";

    private final RouteScoringProperties routeScoringProperties;

    public PoiLinearFeatureExtractor(RouteScoringProperties routeScoringProperties) {
        this.routeScoringProperties = routeScoringProperties;
    }

    public PoiLinearFeatures extract(
            int index,
            List<PoiCandidateDTO> candidates,
            List<PoiSemanticProfile> semanticProfiles,
            double[] distanceToCenter,
            List<String> matchedRequestTags,
            LinearScoringContext env
    ) {
        PoiCandidateDTO candidate = candidates.get(index);
        PoiSemanticProfile semantic = semanticProfiles.get(index);
        boolean transportOn = env.transportSignalAvailable();

        // —— W_quality ——
        boolean ratingMissing = candidate.amapRating() == null;
        double ratingNorm = ratingMissing
                ? this.linearConstant("rating-missing-default")
                : RouteScoringProperties.clamp01(candidate.amapRating().doubleValue() / this.linearConstant("rating-full"));
        double hasImage = candidate.hasImage() ? 1d : 0d;
        double missingInfoRisk = this.feature("missing-info.image-weight") * (hasImage == 0d ? 1d : 0d)
                + this.feature("missing-info.rating-weight") * (ratingMissing ? 1d : 0d);

        // —— W_budget（消费类门控）——
        boolean consumable = semantic.isConsumable();
        double avgPriceNorm = 0d;
        double isPriceMissing = 0d;
        double isFree = 0d;
        double expensivePoiRisk = 0d;
        if (consumable) {
            Integer cost = candidate.avgPriceCent();
            if (cost == null) {
                avgPriceNorm = this.linearConstant("price-missing-default");
                isPriceMissing = 1d;
            } else {
                avgPriceNorm = Math.min(cost / this.linearConstant("budget-cap-cent"), this.linearConstant("price-norm-cap"));
                isFree = cost == 0 ? 1d : 0d;
            }
            expensivePoiRisk = RouteScoringProperties.clamp01(avgPriceNorm - this.feature("expensive-risk-offset"));
        }

        // —— W_distance（distanceNorm + pool-derived）——
        double effectiveRadius = Math.max(1d, env.effectiveRadiusMeters());
        double distanceMeters = distanceToCenter[index];
        double distanceNorm = distanceMeters < 0d
                ? this.feature("unknown-distance.norm")
                : Math.min(distanceMeters / effectiveRadius, this.linearConstant("distance-norm-cap"));
        double fatigueRef = Math.max(1d, env.fatigueRefMeters());
        double distanceFatiguePressure = distanceMeters < 0d
                ? this.feature("unknown-distance.fatigue-pressure")
                : RouteScoringProperties.clamp01(distanceMeters / fatigueRef);

        PoolDerived poolDerived = this.poolDerived(index, candidates, semanticProfiles, effectiveRadius);

        // —— W_transport ——
        double transitHigh = 0d;
        double transitMedium = 0d;
        double transitLow = 0d;
        double nearestTransitDistanceNorm = 0d;
        double walkingAccessibility = 0d;
        if (transportOn) {
            TransitResult transit = this.transitFeatures(candidate, env.transportProfile());
            transitHigh = transit.high();
            transitMedium = transit.medium();
            transitLow = transit.low();
            nearestTransitDistanceNorm = transit.distanceNorm();
            walkingAccessibility = this.walkingAccessibility(distanceMeters, transit.transitScore());
        }
        double rainTransportRisk = transportOn ? env.rainLevel() * (1d - walkingAccessibility) : 0d;

        // —— env cross ——
        double weatherOutdoorRisk = env.badWeatherSeverity() * semantic.weatherSensitive();
        double heatFatigueRisk = env.heatLevel() * distanceFatiguePressure;
        double closeRisk = this.closeRisk(candidate, env.routeStartMinutes());

        // —— W_goal 时间窗 cross ——
        RouteTimeStructure timeStructure = env.routeTimeStructure();
        double nightMatch = timeStructure.isNight() && semantic.nightFriendly() ? 1d : 0d;
        boolean mealCandidate = semantic.isMealCandidate();
        double mealMatch = timeStructure.isMealWindow() && mealCandidate ? 1d : 0d;

        // —— W_interest ——
        // POI 侧用语义映射出的 poiTagHits（POI 全量标签）而非 matchedInterestTags（仅入口标签，
        // 恒约 1 个会把 ratio 锁死在 1/|requestTags|）。只数与 request.interestTags 相交的部分，
        // 语义丰富不等于请求命中多。
        double interestMatchRatio = this.interestMatchRatio(semantic.poiTagHits(), matchedRequestTags);

        // —— W_personalization（× profileConfidence；guard 见各项）——
        UserPreferenceProfileDTO profile = env.profile();
        double confidence = profile.profileConfidence() == null ? 0d : profile.profileConfidence().doubleValue();
        double userInterestAffinity = this.userInterestAffinity(
                profile,
                semantic.poiTagHits(),
                env.interestTagCatalog(),
                env.affinityNorm(),
                confidence
        );
        double personalizedDistancePressure = confidence * doubleOf(profile.distanceSensitivity()) * distanceNorm;
        double personalizedBudgetPressure = confidence * doubleOf(profile.budgetSensitivity()) * avgPriceNorm;
        double personalizedTransitPressure = transportOn
                ? confidence * doubleOf(profile.transferSensitivity()) * nearestTransitDistanceNorm
                : 0d;
        double personalizedExplorationMatch = confidence * doubleOf(profile.hiddenGemAffinity()) * (semantic.hiddenGem() ? 1d : 0d);

        return new PoiLinearFeatures(
                interestMatchRatio,
                semantic.classic() ? 1d : 0d,
                semantic.local() ? 1d : 0d,
                semantic.photoFriendly() ? 1d : 0d,
                semantic.nightFriendly() ? 1d : 0d,
                semantic.quiet() ? 1d : 0d,
                semantic.hiddenGem() ? 1d : 0d,
                nightMatch,
                mealMatch,
                ratingNorm,
                hasImage,
                ratingMissing ? 1d : 0d,
                transitHigh,
                transitMedium,
                transitLow,
                nearestTransitDistanceNorm,
                walkingAccessibility,
                rainTransportRisk,
                distanceNorm,
                poolDerived.isolatedDistanceNorm(),
                poolDerived.clusterConnectivity(),
                distanceFatiguePressure,
                heatFatigueRisk,
                avgPriceNorm,
                isPriceMissing,
                isFree,
                expensivePoiRisk,
                closeRisk,
                weatherOutdoorRisk,
                poolDerived.categoryDuplicateRisk(),
                missingInfoRisk,
                userInterestAffinity,
                personalizedDistancePressure,
                personalizedBudgetPressure,
                personalizedTransitPressure,
                personalizedExplorationMatch,
                distanceMeters,
                effectiveRadius
        );
    }

    private PoolDerived poolDerived(
            int index,
            List<PoiCandidateDTO> candidates,
            List<PoiSemanticProfile> semanticProfiles,
            double effectiveRadius
    ) {
        GeoPointDTO origin = candidates.get(index).location();
        Set<String> myGroups = semanticProfiles.get(index).categoryGroups();
        if (origin == null) {
            return new PoolDerived(0d, 0d, 0d);
        }
        double nearestOther = Double.MAX_VALUE;
        int neighborCount = 0;
        int duplicateCount = 0;
        for (int other = 0; other < candidates.size(); other++) {
            if (other == index) {
                continue;
            }
            GeoPointDTO otherLocation = candidates.get(other).location();
            if (otherLocation != null) {
                double d = GeoMath.distanceMeters(origin, otherLocation);
                nearestOther = Math.min(nearestOther, d);
                if (d <= this.linearConstant("neighbor-radius-meters")) {
                    neighborCount++;
                }
            }
            if (!myGroups.isEmpty() && this.sharesGroup(myGroups, semanticProfiles.get(other).categoryGroups())) {
                duplicateCount++;
            }
        }
        double isolationRef = Math.max(1d, effectiveRadius / this.feature("isolation-ref-divisor"));
        double isolatedDistanceNorm = nearestOther == Double.MAX_VALUE
                ? 0d
                : Math.min(nearestOther / isolationRef, this.linearConstant("distance-norm-cap"));
        double clusterConnectivity = Math.min(neighborCount / this.linearConstant("connect-full"), 1d);
        double categoryDuplicateRisk = myGroups.isEmpty()
                ? 0d
                : Math.min(duplicateCount / this.linearConstant("dup-full"), 1d);
        return new PoolDerived(isolatedDistanceNorm, clusterConnectivity, categoryDuplicateRisk);
    }

    private boolean sharesGroup(Set<String> a, Set<String> b) {
        for (String group : a) {
            if (b.contains(group)) {
                return true;
            }
        }
        return false;
    }

    private TransitResult transitFeatures(PoiCandidateDTO candidate, TransportProfile transportProfile) {
        TransitLookupStatus status = candidate.transitLookupStatus();
        if (status == TransitLookupStatus.UNAVAILABLE || status == TransitLookupStatus.FAILED) {
            // 批量不可用/失败会由 transportSignalAvailable=false 整体 mask；若未来出现单 POI 局部失败，按中性交通档降级。
            return new TransitResult(
                    0d,
                    this.feature("transit-local-fail.medium"),
                    0d,
                    this.feature("transit-local-fail.distance-norm"),
                    this.feature("transit-local-fail.score")
            );
        }
        Integer nearestMeters = this.nearestTransitMeters(candidate.nearestTransit(), transportProfile);
        // SUCCESS_EMPTY / SUCCESS 但无站点：事实性差，归 transitLow + 站距 cap。
        if (nearestMeters == null) {
            return new TransitResult(
                    0d,
                    0d,
                    this.feature("transit-empty.low"),
                    this.feature("transit-empty.distance-norm"),
                    this.feature("transit-empty.score")
            );
        }
        double distanceNorm = Math.min(nearestMeters / this.linearConstant("transit-ref-meters"), this.linearConstant("transit-dist-cap"));
        if (nearestMeters <= this.featureExtractorInt("transit-high-meters")) {
            return new TransitResult(1d, 0d, 0d, distanceNorm, 1d);
        }
        if (nearestMeters <= this.featureExtractorInt("transit-medium-meters")) {
            return new TransitResult(0d, 1d, 0d, distanceNorm, 0.5d);
        }
        return new TransitResult(0d, 0d, 1d, distanceNorm, 0d);
    }

    private Integer nearestTransitMeters(List<TransitFacilityDTO> nearestTransit, TransportProfile transportProfile) {
        if (nearestTransit == null || nearestTransit.isEmpty()) {
            return null;
        }
        String requiredType = this.requiredTransitType(transportProfile);
        return nearestTransit.stream()
                .filter(transit -> requiredType == null || requiredType.equals(transit.type()))
                .map(TransitFacilityDTO::distanceMeters)
                .filter(distanceMeters -> distanceMeters != null)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private String requiredTransitType(TransportProfile transportProfile) {
        if (transportProfile == null) {
            return null;
        }
        return switch (transportProfile) {
            case WALK_BUS -> TRANSIT_TYPE_BUS;
            case WALK_SUBWAY, BIKE_SUBWAY -> TRANSIT_TYPE_SUBWAY;
            case WALK_ONLY, WALK_TRANSIT, WALK_TAXI -> null;
        };
    }

    private double walkingAccessibility(double distanceMeters, double transitScore) {
        double walkPart = distanceMeters < 0d
                ? this.feature("walk-access.unknown-walk-part")
                : RouteScoringProperties.clamp01(1d - distanceMeters / this.linearConstant("walk-ref-meters"));
        return this.feature("walk-access.walk-weight") * walkPart
                + this.feature("walk-access.transit-weight") * transitScore;
    }

    private double closeRisk(PoiCandidateDTO candidate, int routeStartMinutes) {
        String opentime = candidate.opentimeToday();
        if (StrUtil.isBlank(opentime) || routeStartMinutes < 0) {
            return this.linearConstant("close-risk-missing-default");
        }
        // opentimeToday 形如 "08:00-16:30" 或 "09:00-12:00,13:00-18:00"；路线开始时刻落在任一营业段内→0，否则→1。
        boolean parsedAny = false;
        for (String segment : opentime.split(",")) {
            int dash = segment.indexOf('-');
            if (dash < 0) {
                continue;
            }
            Integer open = this.toMinutes(segment.substring(0, dash));
            Integer close = this.toMinutes(segment.substring(dash + 1));
            if (open == null || close == null) {
                continue;
            }
            parsedAny = true;
            boolean within = close > open
                    ? routeStartMinutes >= open && routeStartMinutes < close
                    : routeStartMinutes >= open || routeStartMinutes < close; // 跨夜营业
            if (within) {
                return 0d;
            }
        }
        return parsedAny ? 1d : this.linearConstant("close-risk-missing-default");
    }

    private Integer toMinutes(String hhmm) {
        String value = hhmm == null ? "" : hhmm.trim();
        int colon = value.indexOf(':');
        if (colon < 0) {
            return null;
        }
        try {
            int hour = Integer.parseInt(value.substring(0, colon).trim());
            int minute = Integer.parseInt(value.substring(colon + 1).trim());
            return hour * 60 + minute;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private double interestMatchRatio(Set<String> poiTagHits, List<String> requestTags) {
        if (requestTags == null || requestTags.isEmpty() || poiTagHits.isEmpty()) {
            return 0d;
        }
        long hit = requestTags.stream().filter(poiTagHits::contains).count();
        return RouteScoringProperties.clamp01((double) hit / requestTags.size());
    }

    private double userInterestAffinity(
            UserPreferenceProfileDTO profile,
            Set<String> poiTagHits,
            List<InterestTagCatalogPO> interestTagCatalog,
            double affinityNorm,
            double confidence
    ) {
        if (confidence <= 0d || affinityNorm <= 0d || poiTagHits.isEmpty()) {
            return 0d;
        }
        double sum = 0d;
        java.util.Map<String, BigDecimal> affinities = InterestTagNormalizer.normalizedAffinities(
                profile.tagAffinities(),
                interestTagCatalog
        );
        for (String tag : InterestTagNormalizer.normalizedPoiTags(poiTagHits, interestTagCatalog)) {
            BigDecimal affinity = affinities.get(tag);
            if (affinity != null) {
                sum += affinity.doubleValue();
            }
        }
        return confidence * RouteScoringProperties.clamp01(sum / affinityNorm);
    }

    private static double doubleOf(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private double linearConstant(String path) {
        return this.routeScoringProperties.linearConstantDouble(path);
    }

    private double feature(String path) {
        return this.routeScoringProperties.featureExtractorDouble(path);
    }

    private int featureExtractorInt(String path) {
        return this.routeScoringProperties.featureExtractorInt(path);
    }

    private record PoolDerived(double isolatedDistanceNorm, double clusterConnectivity, double categoryDuplicateRisk) {
    }

    private record TransitResult(double high, double medium, double low, double distanceNorm, double transitScore) {
    }
}
