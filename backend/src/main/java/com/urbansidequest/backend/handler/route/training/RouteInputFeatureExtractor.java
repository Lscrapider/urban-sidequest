package com.urbansidequest.backend.handler.route.training;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.constant.DateTimeFormatConstant;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.domain.dto.RouteSegmentDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.RouteSegmentSource;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.handler.route.SegmentModeResolver;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.LinearScoreConstants;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticProfile;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import com.urbansidequest.backend.handler.route.support.MealWindowSupport;
import com.urbansidequest.backend.handler.route.support.RouteStopIdSupport;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RouteInputFeatureExtractor {

    private static final int MAX_STOP_COUNT = 8;

    private static final int TOP_TAG_K = 3;

    private static final double STAY_BUDGET_RATIO = 0.85d;

    private static final double SEGMENT_COMFORT_DURATION_MINUTES = 20d;

    private static final double MIN_SEGMENT_COMFORT_DISTANCE_METERS = 500d;

    private static final double LONG_SEGMENT_PRESSURE_THRESHOLD = 1.0d;

    private static final double VISITED_VICINITY_THRESHOLD_METERS = 300d;

    private static final double RISK_COST_THRESHOLD = -0.12d;

    private static final String MISSING_AMAP_TYPECODE_PREFIX = "__MISSING_AMAP_TYPECODE__";

    private final ObjectMapper objectMapper;

    private final PoiSemanticResolver poiSemanticResolver;

    private final SegmentModeResolver segmentModeResolver;

    public RouteInputFeatureExtractor(
            ObjectMapper objectMapper,
            PoiSemanticResolver poiSemanticResolver,
            SegmentModeResolver segmentModeResolver
    ) {
        this.objectMapper = objectMapper;
        this.poiSemanticResolver = poiSemanticResolver;
        this.segmentModeResolver = segmentModeResolver;
    }

    public RouteInputFeatureSnapshot extract(CandidateRouteDTO route, RouteGenerationContext context) {
        FeatureSource source = this.toFeatureSource(route, context);
        List<Map<String, Object>> stopMatrix = this.stopMatrix(route, source);
        List<SegmentFeature> segmentFeatures = this.segmentFeatures(route, context, source);
        List<Map<String, Object>> segmentMatrix = this.segmentMatrix(route, context, segmentFeatures);
        Map<String, Object> routeDerivedVector = this.routeDerivedVector(route, context, source, stopMatrix, segmentMatrix, segmentFeatures);
        Map<String, Object> contextCrossVector = this.contextCrossVector(context, routeDerivedVector, route, source);
        Map<String, Object> contextJson = this.contextJson(context);
        return new RouteInputFeatureSnapshot(
                RoutePreferenceFeatureSchema.VERSION,
                this.writeJson(stopMatrix),
                this.writeJson(segmentMatrix),
                this.writeJson(routeDerivedVector),
                this.writeJson(contextCrossVector),
                this.writeJson(contextJson)
        );
    }

    private FeatureSource toFeatureSource(CandidateRouteDTO route, RouteGenerationContext context) {
        Map<String, PoiCandidateDTO> candidatesByPoiId = new LinkedHashMap<>();
        for (PoiCandidateDTO candidate : context.getPoiCandidates()) {
            candidatesByPoiId.put(candidate.poiId(), candidate);
        }

        Map<String, PoiLinearTraceDTO> tracesByPoiId = new LinkedHashMap<>();
        for (PoiLinearTraceDTO trace : context.getPoiLinearTraces()) {
            tracesByPoiId.put(trace.poiId(), trace);
        }

        Map<String, PoiSemanticProfile> semanticByPoiId = new LinkedHashMap<>();
        for (RouteStopDTO stop : route.stops()) {
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiCandidateDTO candidate = candidatesByPoiId.get(poiId);
            semanticByPoiId.put(poiId, candidate == null
                    ? PoiSemanticProfile.empty()
                    : this.poiSemanticResolver.resolve(candidate, context.getPoiSemanticMappings()));
        }
        return new FeatureSource(candidatesByPoiId, tracesByPoiId, semanticByPoiId, context.getInterestTagCatalog());
    }

    private List<Map<String, Object>> stopMatrix(CandidateRouteDTO route, FeatureSource source) {
        List<RouteStopDTO> stops = route.stops();
        List<Map<String, Object>> rows = new ArrayList<>();
        int stopCount = stops.size();
        for (int index = 0; index < stopCount; index++) {
            RouteStopDTO stop = stops.get(index);
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiCandidateDTO candidate = source.candidatesByPoiId().get(poiId);
            PoiSemanticProfile semantic = source.semanticByPoiId().getOrDefault(poiId, PoiSemanticProfile.empty());
            PoiLinearTraceDTO trace = source.tracesByPoiId().get(poiId);
            Map<String, Object> row = new LinkedHashMap<>();

            row.put("isClassic", bit(semantic.classic()));
            row.put("isLocal", bit(semantic.local()));
            row.put("isPhotoFriendly", bit(semantic.photoFriendly()));
            row.put("isNightFriendly", bit(semantic.nightFriendly()));
            row.put("isQuiet", bit(semantic.quiet()));
            row.put("isHiddenGem", bit(semantic.hiddenGem()));

            BigDecimal rating = stop.rating() == null && candidate != null ? candidate.amapRating() : stop.rating();
            row.put("ratingNorm", rating == null ? LinearScoreConstants.RATING_MISSING_DEFAULT
                    : clamp01(rating.doubleValue() / LinearScoreConstants.RATING_FULL));
            row.put("hasImage", bit((stop.imageUrls() != null && !stop.imageUrls().isEmpty())
                    || (candidate != null && candidate.hasImage())));
            row.put("isRatingMissing", bit(rating == null));

            Integer avgPriceCent = candidate == null ? null : candidate.avgPriceCent();
            row.put("avgPriceNorm", avgPriceCent == null ? LinearScoreConstants.PRICE_MISSING_DEFAULT
                    : Math.min(avgPriceCent / LinearScoreConstants.BUDGET_CAP_CENT, LinearScoreConstants.PRICE_NORM_CAP));
            row.put("isPriceMissing", bit(avgPriceCent == null));
            row.put("isFree", bit(avgPriceCent != null && avgPriceCent == 0));
            row.put("expensivePoiRisk", avgPriceCent == null ? 0d
                    : clamp01(avgPriceCent / LinearScoreConstants.BUDGET_CAP_CENT - 1.0d));

            row.put("closeRisk", stop.riskNote() == null || stop.riskNote().isBlank() ? 0d : LinearScoreConstants.CLOSE_RISK_MISSING_DEFAULT);
            row.put("missingInfoRisk", bit(candidate == null || isBlank(stop.description())));

            TransitFeature transit = this.transitFeature(candidate);
            row.put("transitHigh", transit.high());
            row.put("transitMedium", transit.medium());
            row.put("transitLow", transit.low());
            row.put("nearestTransitDistanceNorm", transit.distanceNorm());

            row.put("interestScore", trace == null ? 0d : trace.interestScore());
            row.put("goalScore", trace == null ? 0d : trace.goalScore());
            row.put("qualityScore", trace == null ? 0d : trace.qualityScore());
            row.put("transportScore", trace == null ? 0d : trace.transportScore());
            row.put("distanceCost", trace == null ? 0d : trace.distanceCost());
            row.put("budgetCost", trace == null ? 0d : trace.budgetCost());
            row.put("riskCost", trace == null ? 0d : trace.riskCost());
            row.put("poiLinearTraceMissing", bit(trace == null));

            row.put("routeRole_MUST_VISIT", bit(this.isRouteRole(stop, semantic, candidate, index, "MUST_VISIT")));
            row.put("routeRole_ANCHOR", bit(this.isRouteRole(stop, semantic, candidate, index, "ANCHOR")));
            row.put("routeRole_MEAL", bit(this.isRouteRole(stop, semantic, candidate, index, "MEAL")));
            row.put("routeRole_REST", bit(this.isRouteRole(stop, semantic, candidate, index, "REST")));
            row.put("routeRole_LOCAL", bit(this.isRouteRole(stop, semantic, candidate, index, "LOCAL")));
            row.put("routeRole_PHOTO", bit(this.isRouteRole(stop, semantic, candidate, index, "PHOTO")));
            row.put("routeRole_BACKUP", bit(this.isRouteRole(stop, semantic, candidate, index, "BACKUP")));

            row.put("stayMinutesNorm", stop.stayMinutes() / 60d);
            row.put("orderPositionNorm", stopCount <= 1 ? 0d : index / (double) (stopCount - 1));
            row.put("isFirstStop", bit(index == 0));
            row.put("isLastStop", bit(index == stopCount - 1));
            rows.add(row);
        }
        return rows;
    }

    private List<SegmentFeature> segmentFeatures(CandidateRouteDTO route, RouteGenerationContext context, FeatureSource source) {
        List<RouteStopDTO> stops = route.stops();
        List<SegmentFeature> features = new ArrayList<>();
        if (stops.size() < 2) {
            return features;
        }
        for (int index = 0; index < stops.size() - 1; index++) {
            RouteStopDTO origin = stops.get(index);
            RouteStopDTO destination = stops.get(index + 1);
            RouteSegmentDTO routeSegment = this.routeSegmentAt(route, index);
            boolean missing = routeSegment == null && (origin.location() == null || destination.location() == null);
            SegmentTransportMode mode = routeSegment == null
                    ? this.segmentModeResolver.resolveInitialMode(
                            context.getGenerateParam().getTransportProfile(),
                            origin,
                            destination,
                            source.candidatesByPoiId(),
                            route.routeCode()
                    )
                    : routeSegment.mode();
            int distanceMeters = routeSegment == null
                    ? (missing ? 0 : GeoMath.distanceMeters(origin.location(), destination.location()))
                    : routeSegment.distanceMeters();
            int durationMinutes = routeSegment == null
                    ? (missing ? 0 : this.estimateDurationMinutes(distanceMeters, mode))
                    : routeSegment.durationMinutes();
            RouteSegmentSource sourceType = routeSegment == null ? null : routeSegment.source();
            features.add(new SegmentFeature(index, mode, distanceMeters, durationMinutes, missing, sourceType));
        }
        return features;
    }

    private List<Map<String, Object>> segmentMatrix(
            CandidateRouteDTO route,
            RouteGenerationContext context,
            List<SegmentFeature> segmentFeatures
    ) {
        List<RouteStopDTO> stops = route.stops();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (segmentFeatures.isEmpty()) {
            return rows;
        }
        double routeComfortDistance = this.routeComfortDistanceMeters(context);
        double segmentComfortDistance = Math.max(MIN_SEGMENT_COMFORT_DISTANCE_METERS, routeComfortDistance / Math.max(1, stops.size() - 1));
        for (SegmentFeature feature : segmentFeatures) {
            double distancePressure = feature.missing() ? 1d : clamp(feature.distanceMeters() / segmentComfortDistance, 0d, 2d);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("straightDistanceNorm", feature.missing() ? 1d : feature.distanceMeters() / segmentComfortDistance);
            row.put("estimatedDurationNorm", feature.missing() ? 1d : feature.durationMinutes() / SEGMENT_COMFORT_DURATION_MINUTES);
            row.put("transportMode_WALK", bit(feature.mode() == SegmentTransportMode.WALK));
            row.put("transportMode_BIKE", bit(feature.mode() == SegmentTransportMode.BIKE));
            row.put("transportMode_BUS", bit(feature.mode() == SegmentTransportMode.BUS));
            row.put("transportMode_SUBWAY", bit(feature.mode() == SegmentTransportMode.SUBWAY));
            row.put("transportMode_TAXI", bit(feature.mode() == SegmentTransportMode.TAXI));
            row.put("isBacktracking", bit(!feature.missing() && this.isBacktracking(stops, feature.index())));
            row.put("distancePressure", distancePressure);
            row.put("segmentCalibrated", bit(feature.calibrated()));
            row.put("segmentAmapFallback", bit(feature.source() == RouteSegmentSource.AMAP_FALLBACK));
            row.put("segmentStraightLineFallback", bit(feature.source() == RouteSegmentSource.FALLBACK_STRAIGHT_LINE));
            row.put("segmentEstimateMissing", bit(feature.missing()));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> routeDerivedVector(
            CandidateRouteDTO route,
            RouteGenerationContext context,
            FeatureSource source,
            List<Map<String, Object>> stopMatrix,
            List<Map<String, Object>> segmentMatrix,
            List<SegmentFeature> segmentFeatures
    ) {
        List<RouteStopDTO> stops = route.stops();
        int stopCount = stops.size();
        int segmentCount = segmentMatrix.size();
        int durationMinutes = Math.max(1, context.getGenerateParam().getDurationMinutes());
        int estimatedStayMinutes = stops.stream().mapToInt(RouteStopDTO::stayMinutes).sum();
        double estimatedTravelMinutes = segmentMatrix.stream()
                .mapToDouble(row -> doubleValue(row.get("estimatedDurationNorm")) * SEGMENT_COMFORT_DURATION_MINUTES)
                .sum();

        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("stopCountNorm", stopCount / (double) MAX_STOP_COUNT);
        vector.put("stayBudgetUsageRatio", estimatedStayMinutes / (durationMinutes * STAY_BUDGET_RATIO));
        vector.put("estimatedTravelMinutesNorm", estimatedTravelMinutes / durationMinutes);
        vector.put("timeBudgetUsageRatio", (estimatedStayMinutes + estimatedTravelMinutes) / durationMinutes);

        boolean requiresLunch = MealWindowSupport.requiresLunch(context.getGenerateParam());
        boolean requiresDinner = MealWindowSupport.requiresDinner(context.getGenerateParam());
        long mealStopCount = stopMatrix.stream().filter(row -> doubleValue(row.get("routeRole_MEAL")) > 0d).count();
        long restStopCount = stopMatrix.stream().filter(row -> doubleValue(row.get("routeRole_REST")) > 0d).count();
        MealCoverage mealCoverage = this.mealCoverage(stops, mealStopCount);
        boolean lunchCovered = mealCoverage.lunchCovered();
        boolean dinnerCovered = mealCoverage.dinnerCovered();
        vector.put("requiresLunchFlag", bit(requiresLunch));
        vector.put("requiresDinnerFlag", bit(requiresDinner));
        vector.put("mealStopCountNorm", mealStopCount / (double) MAX_STOP_COUNT);
        vector.put("restStopCountNorm", restStopCount / (double) MAX_STOP_COUNT);
        vector.put("lunchCoveredFlag", bit(lunchCovered));
        vector.put("dinnerCoveredFlag", bit(dinnerCovered));
        vector.put("missingRequiredMealFlag", bit((requiresLunch && !lunchCovered) || (requiresDinner && !dinnerCovered)));

        double routeComfortDistance = this.routeComfortDistanceMeters(context);
        double segmentComfortDistance = Math.max(MIN_SEGMENT_COMFORT_DISTANCE_METERS, routeComfortDistance / Math.max(1, stopCount - 1));
        double totalDistanceNorm = segmentMatrix.stream().mapToDouble(row -> doubleValue(row.get("straightDistanceNorm")) * segmentComfortDistance).sum()
                / routeComfortDistance;
        vector.put("totalDistanceNorm", totalDistanceNorm);
        vector.put("maxSegmentDistanceNorm", segmentMatrix.stream().mapToDouble(row -> doubleValue(row.get("straightDistanceNorm"))).max().orElse(0d));
        vector.put("avgSegmentDistanceNorm", segmentMatrix.stream().mapToDouble(row -> doubleValue(row.get("straightDistanceNorm"))).average().orElse(0d));
        vector.put("longSegmentRatio", segmentCount == 0 ? 0d : segmentMatrix.stream()
                .filter(row -> doubleValue(row.get("distancePressure")) > LONG_SEGMENT_PRESSURE_THRESHOLD)
                .count() / (double) segmentCount);
        vector.put("backtrackingSegmentRatio", segmentCount == 0 ? 0d : segmentMatrix.stream()
                .filter(row -> doubleValue(row.get("isBacktracking")) > 0d)
                .count() / (double) segmentCount);
        long transferSegmentCount = segmentMatrix.stream().filter(this::isTransferSegment).count();
        double transferDistancePressure = segmentCount == 0 ? 0d : segmentMatrix.stream()
                .filter(this::isTransferSegment)
                .mapToDouble(row -> doubleValue(row.get("distancePressure")))
                .sum() / segmentCount;
        vector.put("transferSegmentRatio", segmentCount == 0 ? 0d : transferSegmentCount / (double) segmentCount);
        vector.put("transferDistancePressure", transferDistancePressure);
        vector.put("fallbackAmapRatio", segmentCount == 0 ? 0d : segmentMatrix.stream()
                .filter(row -> doubleValue(row.get("segmentAmapFallback")) > 0d)
                .count() / (double) segmentCount);
        vector.put("straightLineFallbackRatio", segmentCount == 0 ? 0d : segmentMatrix.stream()
                .filter(row -> doubleValue(row.get("segmentStraightLineFallback")) > 0d)
                .count() / (double) segmentCount);
        vector.put("missingSegmentRatio", segmentCount == 0 ? 0d : segmentMatrix.stream()
                .filter(row -> doubleValue(row.get("segmentEstimateMissing")) > 0d)
                .count() / (double) segmentCount);

        ModeDistanceStats modeDistanceStats = this.modeDistanceStats(segmentFeatures);
        vector.put("walkDistanceRatio", modeDistanceStats.walkRatio());
        vector.put("bikeDistanceRatio", modeDistanceStats.bikeRatio());
        vector.put("busDistanceRatio", modeDistanceStats.busRatio());
        vector.put("subwayDistanceRatio", modeDistanceStats.subwayRatio());
        vector.put("taxiDistanceRatio", modeDistanceStats.taxiRatio());

        vector.put("interestCoverageRatio", this.interestCoverageRatio(context, route, source));
        vector.put("localStopRatio", this.avg(stopMatrix, "isLocal"));
        vector.put("classicStopRatio", this.avg(stopMatrix, "isClassic"));
        vector.put("photoFriendlyStopRatio", this.avg(stopMatrix, "isPhotoFriendly"));
        vector.put("nightFriendlyStopRatio", this.avg(stopMatrix, "isNightFriendly"));
        vector.put("quietStopRatio", this.avg(stopMatrix, "isQuiet"));
        vector.put("hiddenGemStopRatio", this.avg(stopMatrix, "isHiddenGem"));

        List<String> categoryGroups = this.categoryGroups(route, source);
        vector.put("categoryDiversityRatio", stopCount == 0 ? 0d : new LinkedHashSet<>(categoryGroups).size() / (double) stopCount);
        vector.put("dominantCategoryRatio", stopCount == 0 ? 0d : this.dominantValueCount(categoryGroups) / (double) stopCount);
        vector.put("consecutiveSameCategoryMaxNorm", stopCount == 0 ? 0d : this.maxConsecutiveValueCount(categoryGroups) / (double) stopCount);
        List<String> amapTypecodeKeys = this.amapTypecodeKeys(route, source);
        long missingAmapTypecodeCount = amapTypecodeKeys.stream()
                .filter(key -> key.startsWith(MISSING_AMAP_TYPECODE_PREFIX))
                .count();
        vector.put("amapTypecodeDiversityRatio", stopCount == 0 ? 0d : new LinkedHashSet<>(amapTypecodeKeys).size() / (double) stopCount);
        vector.put("dominantAmapTypecodeRatio", stopCount == 0 ? 0d : this.dominantValueCount(amapTypecodeKeys) / (double) stopCount);
        vector.put("consecutiveSameAmapTypecodeMaxNorm", stopCount == 0 ? 0d : this.maxConsecutiveValueCount(amapTypecodeKeys) / (double) stopCount);
        vector.put("missingAmapTypecodeRatio", stopCount == 0 ? 0d : missingAmapTypecodeCount / (double) stopCount);

        BudgetStats budgetStats = this.budgetStats(route, context, source);
        vector.put("budgetTotalNorm", budgetStats.budgetTotalNorm());
        vector.put("budgetPressure", budgetStats.budgetPressure());
        vector.put("missingPriceRatio", budgetStats.missingPriceRatio());

        vector.put("avgInterestScore", this.avg(stopMatrix, "interestScore"));
        vector.put("avgGoalScore", this.avg(stopMatrix, "goalScore"));
        vector.put("avgQualityScore", this.avg(stopMatrix, "qualityScore"));
        vector.put("avgRiskCost", this.avg(stopMatrix, "riskCost"));
        vector.put("highRiskStopRatio", stopCount == 0 ? 0d : stopMatrix.stream()
                .filter(row -> doubleValue(row.get("riskCost")) <= RISK_COST_THRESHOLD)
                .count() / (double) stopCount);
        return vector;
    }

    private Map<String, Object> contextJson(RouteGenerationContext context) {
        Map<String, Object> contextJson = new LinkedHashMap<>();
        RouteTimeStructure timeStructure = RouteTimeStructure.fromWindow(
                context.getGenerateParam().getDepartureTime(),
                context.getGenerateParam().getDurationMinutes()
        );
        contextJson.put("routeGoal", context.getGenerateParam().getRouteGoal());
        contextJson.put("transportProfile", context.getGenerateParam().getTransportProfile());
        contextJson.put("budgetLevel", context.getGenerateParam().getBudgetLevel());
        contextJson.put("interestTags", context.getGenerateParam().getInterestTags());
        contextJson.put("mealWindows", context.getGenerateParam().getMealWindows());
        contextJson.put("departureTime", this.localDateTimeText(context.getGenerateParam().getDepartureTime()));
        contextJson.put("durationMinutes", context.getGenerateParam().getDurationMinutes());
        contextJson.put("routeTimeStructure", timeStructure);
        contextJson.put("weather", context.getRouteWeather());
        contextJson.put("userPreferenceProfile", context.getUserPreferenceProfile());
        return contextJson;
    }

    private Map<String, Object> contextCrossVector(
            RouteGenerationContext context,
            Map<String, Object> routeDerivedVector,
            CandidateRouteDTO route,
            FeatureSource source
    ) {
        Map<String, Object> vector = new LinkedHashMap<>();
        UserPreferenceProfileDTO profile = context.getUserPreferenceProfile();
        double profileConfidence = profile == null ? 0d : doubleOf(profile.profileConfidence());
        double distanceSensitivity = profile == null ? 0d : doubleOf(profile.distanceSensitivity());
        double budgetSensitivity = profile == null ? 0d : doubleOf(profile.budgetSensitivity());
        double transferSensitivity = profile == null ? 0d : doubleOf(profile.transferSensitivity());
        double hiddenGemAffinity = profile == null ? 0d : doubleOf(profile.hiddenGemAffinity());

        double totalDistanceNorm = doubleValue(routeDerivedVector.get("totalDistanceNorm"));
        double maxSegmentDistanceNorm = doubleValue(routeDerivedVector.get("maxSegmentDistanceNorm"));
        double transferDistancePressure = doubleValue(routeDerivedVector.get("transferDistancePressure"));
        double budgetPressure = doubleValue(routeDerivedVector.get("budgetPressure"));
        double hiddenGemStopRatio = doubleValue(routeDerivedVector.get("hiddenGemStopRatio"));
        double localStopRatio = doubleValue(routeDerivedVector.get("localStopRatio"));
        double classicStopRatio = doubleValue(routeDerivedVector.get("classicStopRatio"));
        double photoFriendlyStopRatio = doubleValue(routeDerivedVector.get("photoFriendlyStopRatio"));
        double nightFriendlyStopRatio = doubleValue(routeDerivedVector.get("nightFriendlyStopRatio"));
        double quietStopRatio = doubleValue(routeDerivedVector.get("quietStopRatio"));
        double highRiskStopRatio = doubleValue(routeDerivedVector.get("highRiskStopRatio"));
        double requiresLunchFlag = doubleValue(routeDerivedVector.get("requiresLunchFlag"));
        double requiresDinnerFlag = doubleValue(routeDerivedVector.get("requiresDinnerFlag"));
        double lunchCoveredFlag = doubleValue(routeDerivedVector.get("lunchCoveredFlag"));
        double dinnerCoveredFlag = doubleValue(routeDerivedVector.get("dinnerCoveredFlag"));
        double walkDistanceRatio = doubleValue(routeDerivedVector.get("walkDistanceRatio"));
        double bikeDistanceRatio = doubleValue(routeDerivedVector.get("bikeDistanceRatio"));
        double busDistanceRatio = doubleValue(routeDerivedVector.get("busDistanceRatio"));
        double subwayDistanceRatio = doubleValue(routeDerivedVector.get("subwayDistanceRatio"));
        double taxiDistanceRatio = doubleValue(routeDerivedVector.get("taxiDistanceRatio"));

        vector.put("profileDistanceTotalPressure", profileConfidence * distanceSensitivity * totalDistanceNorm);
        vector.put("profileDistanceMaxSegmentPressure", profileConfidence * distanceSensitivity * maxSegmentDistanceNorm);
        vector.put("profileTransferPressure", profileConfidence * transferSensitivity * transferDistancePressure);
        vector.put("profileBudgetPressure", profileConfidence * budgetSensitivity * budgetPressure);
        vector.put("profileHiddenGemMatch", profileConfidence * hiddenGemAffinity * hiddenGemStopRatio);
        TagAffinityStats tagAffinityStats = this.profileTagAffinityStats(profile, profileConfidence, route, source);
        vector.put("profileTagAffinityCoverage", tagAffinityStats.coverage());
        vector.put("profileTagAffinityPrecision", tagAffinityStats.precision());
        vector.put("profileTagAffinityJaccard", tagAffinityStats.jaccard());
        vector.put("profileTopTagHitRatio", tagAffinityStats.topTagHitRatio());

        RouteGoal routeGoal = context.getGenerateParam().getRouteGoal();
        vector.put("goalLocalMatch", bit(RouteGoal.LOCAL == routeGoal) * localStopRatio);
        vector.put("goalClassicMatch", bit(RouteGoal.CLASSIC == routeGoal) * classicStopRatio);
        vector.put("goalQuietMatch", bit(RouteGoal.QUIET == routeGoal) * quietStopRatio);
        vector.put("goalPhotoMatch", bit(RouteGoal.PHOTO == routeGoal) * photoFriendlyStopRatio);
        vector.put("goalNightMatch", bit(RouteGoal.NIGHT == routeGoal) * nightFriendlyStopRatio);
        vector.put("goalSteadyDistancePressure", bit(RouteGoal.STEADY == routeGoal) * totalDistanceNorm);
        vector.put("goalSteadyRiskPressure", bit(RouteGoal.STEADY == routeGoal) * highRiskStopRatio);

        TransportProfile transportProfile = context.getGenerateParam().getTransportProfile();
        vector.put("walkOnlyTotalDistancePressure", bit(TransportProfile.WALK_ONLY == transportProfile) * totalDistanceNorm);
        vector.put("walkOnlyMaxSegmentPressure", bit(TransportProfile.WALK_ONLY == transportProfile) * maxSegmentDistanceNorm);
        vector.put("walkBusDistancePressure", bit(TransportProfile.WALK_BUS == transportProfile) * totalDistanceNorm);
        vector.put("walkSubwayDistancePressure", bit(TransportProfile.WALK_SUBWAY == transportProfile) * totalDistanceNorm);
        vector.put("walkTransitDistancePressure", bit(TransportProfile.WALK_TRANSIT == transportProfile) * totalDistanceNorm);
        vector.put("bikeSubwayDistancePressure", bit(TransportProfile.BIKE_SUBWAY == transportProfile) * totalDistanceNorm);
        vector.put("walkTaxiBudgetPressure", bit(TransportProfile.WALK_TAXI == transportProfile) * budgetPressure);
        vector.put(
                "profileActualModeFitRatio",
                this.profileActualModeFitRatio(
                        transportProfile,
                        walkDistanceRatio,
                        bikeDistanceRatio,
                        busDistanceRatio,
                        subwayDistanceRatio,
                        taxiDistanceRatio
                )
        );

        RouteTimeStructure timeStructure = RouteTimeStructure.fromWindow(
                context.getGenerateParam().getDepartureTime(),
                context.getGenerateParam().getDurationMinutes()
        );
        vector.put("lunchRequiredMissingMeal", requiresLunchFlag * (1d - lunchCoveredFlag));
        vector.put("dinnerRequiredMissingMeal", requiresDinnerFlag * (1d - dinnerCoveredFlag));
        vector.put("nightRouteNightFriendlyMatch", bit(timeStructure.isNight()) * nightFriendlyStopRatio);
        return vector;
    }

    private double interestCoverageRatio(RouteGenerationContext context, CandidateRouteDTO route, FeatureSource source) {
        Set<String> requestTags = new LinkedHashSet<>(context.getGenerateParam().getInterestTags());
        if (requestTags.isEmpty()) {
            return 0d;
        }
        Set<String> hits = new LinkedHashSet<>();
        for (RouteStopDTO stop : route.stops()) {
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            hits.addAll(source.semanticByPoiId().getOrDefault(poiId, PoiSemanticProfile.empty()).poiTagHits());
        }
        hits.retainAll(requestTags);
        return hits.size() / (double) requestTags.size();
    }

    private BudgetStats budgetStats(CandidateRouteDTO route, RouteGenerationContext context, FeatureSource source) {
        int budgetRelevantCount = 0;
        int missingPriceCount = 0;
        int totalCent = 0;
        for (RouteStopDTO stop : route.stops()) {
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiCandidateDTO candidate = source.candidatesByPoiId().get(poiId);
            PoiSemanticProfile semantic = source.semanticByPoiId().getOrDefault(poiId, PoiSemanticProfile.empty());
            boolean relevant = semantic.isConsumable()
                    || this.isRouteRole(stop, semantic, candidate, -1, "MEAL")
                    || this.isRouteRole(stop, semantic, candidate, -1, "REST");
            if (!relevant) {
                continue;
            }
            budgetRelevantCount++;
            if (candidate == null || candidate.avgPriceCent() == null) {
                missingPriceCount++;
            } else {
                totalCent += candidate.avgPriceCent();
            }
        }
        if (budgetRelevantCount == 0) {
            return new BudgetStats(0d, 0d, 0d);
        }
        double budgetTotalNorm = totalCent / this.budgetCapCent(context.getGenerateParam().getBudgetLevel());
        return new BudgetStats(
                budgetTotalNorm,
                clamp(budgetTotalNorm - 1d, 0d, 2d),
                missingPriceCount / (double) budgetRelevantCount
        );
    }

    private ModeDistanceStats modeDistanceStats(List<SegmentFeature> segmentFeatures) {
        double walkMeters = 0d;
        double bikeMeters = 0d;
        double busMeters = 0d;
        double subwayMeters = 0d;
        double taxiMeters = 0d;
        double totalMeters = 0d;
        for (SegmentFeature feature : segmentFeatures) {
            if (feature.missing() || feature.distanceMeters() <= 0) {
                continue;
            }
            totalMeters += feature.distanceMeters();
            switch (feature.mode()) {
                case WALK -> walkMeters += feature.distanceMeters();
                case BIKE -> bikeMeters += feature.distanceMeters();
                case BUS, TRANSIT -> busMeters += feature.distanceMeters();
                case SUBWAY -> subwayMeters += feature.distanceMeters();
                case TAXI, DRIVE -> taxiMeters += feature.distanceMeters();
            }
        }
        if (totalMeters <= 0d) {
            return ModeDistanceStats.zero();
        }
        return new ModeDistanceStats(
                walkMeters / totalMeters,
                bikeMeters / totalMeters,
                busMeters / totalMeters,
                subwayMeters / totalMeters,
                taxiMeters / totalMeters
        );
    }

    private double profileActualModeFitRatio(
            TransportProfile profile,
            double walkDistanceRatio,
            double bikeDistanceRatio,
            double busDistanceRatio,
            double subwayDistanceRatio,
            double taxiDistanceRatio
    ) {
        if (profile == null) {
            return 0d;
        }
        return switch (profile) {
            case WALK_ONLY -> walkDistanceRatio;
            case WALK_BUS -> walkDistanceRatio + busDistanceRatio;
            case WALK_SUBWAY -> walkDistanceRatio + subwayDistanceRatio;
            case WALK_TRANSIT -> walkDistanceRatio + busDistanceRatio + subwayDistanceRatio;
            case BIKE_SUBWAY -> walkDistanceRatio + bikeDistanceRatio + subwayDistanceRatio;
            case WALK_TAXI -> walkDistanceRatio + taxiDistanceRatio;
        };
    }

    private List<String> categoryGroups(CandidateRouteDTO route, FeatureSource source) {
        List<String> groups = new ArrayList<>();
        for (RouteStopDTO stop : route.stops()) {
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiSemanticProfile semantic = source.semanticByPoiId().getOrDefault(poiId, PoiSemanticProfile.empty());
            String primaryCategoryGroup = semantic.primaryCategoryGroup();
            groups.add(primaryCategoryGroup == null || primaryCategoryGroup.isBlank()
                    ? this.normalizeCategoryFallback(stop.category())
                    : primaryCategoryGroup);
        }
        return groups;
    }

    private String normalizeCategoryFallback(String category) {
        if (category == null || category.isBlank()) {
            return "UNKNOWN";
        }
        return switch (category) {
            case "REST" -> "DRINK";
            case "LOCAL", "NIGHT", "PHOTO", "MUST_VISIT" -> "UNKNOWN";
            default -> category;
        };
    }

    private List<String> amapTypecodeKeys(CandidateRouteDTO route, FeatureSource source) {
        List<String> keys = new ArrayList<>();
        List<RouteStopDTO> stops = route.stops();
        for (int index = 0; index < stops.size(); index++) {
            RouteStopDTO stop = stops.get(index);
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiCandidateDTO candidate = source.candidatesByPoiId().get(poiId);
            String key = candidate == null ? null : candidate.typecode();
            keys.add(isBlank(key) ? MISSING_AMAP_TYPECODE_PREFIX + index : key.trim());
        }
        return keys;
    }

    private int dominantValueCount(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        return counts.values().stream().max(Comparator.naturalOrder()).orElse(0);
    }

    private int maxConsecutiveValueCount(List<String> values) {
        int max = 0;
        int current = 0;
        String previous = null;
        for (String value : values) {
            if (value.equals(previous)) {
                current++;
            } else {
                current = 1;
                previous = value;
            }
            max = Math.max(max, current);
        }
        return max;
    }

    private boolean isBacktracking(List<RouteStopDTO> stops, int segmentIndex) {
        if (segmentIndex < 1) {
            return false;
        }
        RouteStopDTO previous = stops.get(segmentIndex - 1);
        RouteStopDTO current = stops.get(segmentIndex);
        RouteStopDTO next = stops.get(segmentIndex + 1);
        int currentToPrevious = GeoMath.distanceMeters(current.location(), previous.location());
        int nextToPrevious = GeoMath.distanceMeters(next.location(), previous.location());
        if (nextToPrevious < currentToPrevious) {
            return true;
        }
        for (int index = 0; index < segmentIndex - 1; index++) {
            if (GeoMath.distanceMeters(next.location(), stops.get(index).location()) < VISITED_VICINITY_THRESHOLD_METERS) {
                return true;
            }
        }
        return false;
    }

    private int estimateDurationMinutes(int distanceMeters, SegmentTransportMode mode) {
        return switch (mode) {
            case BIKE -> (int) Math.ceil(distanceMeters / 180d) + 3;
            case BUS, SUBWAY, TRANSIT -> (int) Math.ceil(distanceMeters / 260d) + 12;
            case TAXI, DRIVE -> (int) Math.ceil(distanceMeters / 300d) + 8;
            case WALK -> (int) Math.ceil(distanceMeters / 70d);
        };
    }

    private TransitFeature transitFeature(PoiCandidateDTO candidate) {
        if (candidate == null || candidate.nearestTransit().isEmpty()) {
            return new TransitFeature(0d, 1d, 0d, 1d);
        }
        Integer nearestMeters = candidate.nearestTransit().stream()
                .map(transit -> transit.distanceMeters())
                .filter(distance -> distance != null)
                .min(Integer::compareTo)
                .orElse(null);
        if (nearestMeters == null) {
            return new TransitFeature(0d, 0d, 1d, LinearScoreConstants.TRANSIT_DIST_CAP);
        }
        double distanceNorm = Math.min(nearestMeters / LinearScoreConstants.TRANSIT_REF_METERS, LinearScoreConstants.TRANSIT_DIST_CAP);
        if (nearestMeters <= 300) {
            return new TransitFeature(1d, 0d, 0d, distanceNorm);
        }
        if (nearestMeters <= 800) {
            return new TransitFeature(0d, 1d, 0d, distanceNorm);
        }
        return new TransitFeature(0d, 0d, 1d, distanceNorm);
    }

    private String localDateTimeText(LocalDateTime localDateTime) {
        return localDateTime == null ? null : DateTimeFormatConstant.BEIJING_LOCAL_DATE_TIME_FORMATTER.format(localDateTime);
    }

    private double routeComfortDistanceMeters(RouteGenerationContext context) {
        TransportProfile profile = context.getGenerateParam().getTransportProfile();
        DurationBucket bucket = DurationBucket.fromMinutes(context.getGenerateParam().getDurationMinutes());
        return profile.routeDistanceRefMeters(bucket);
    }

    private double budgetCapCent(BudgetLevel budgetLevel) {
        return switch (budgetLevel) {
            case LOW -> 10000d;
            case NORMAL -> LinearScoreConstants.BUDGET_CAP_CENT;
            case FLEXIBLE -> 30000d;
        };
    }

    private boolean isRouteRole(
            RouteStopDTO stop,
            PoiSemanticProfile semantic,
            PoiCandidateDTO candidate,
            int index,
            String routeRole
    ) {
        if (!isBlank(stop.routeRole())) {
            return routeRole.equalsIgnoreCase(stop.routeRole());
        }
        return switch (routeRole) {
            case "MUST_VISIT" -> candidate != null && candidate.mustVisit();
            case "ANCHOR" -> index == 0;
            case "MEAL" -> semantic.isMealCandidate();
            case "REST" -> semantic.isRestCandidate();
            case "LOCAL" -> semantic.local() || semantic.localExperienceCandidate();
            case "PHOTO" -> semantic.photoFriendly();
            case "BACKUP" -> this.containsAny(stop.slotLabel(), "备选", "backup");
            default -> false;
        };
    }

    private MealCoverage mealCoverage(List<RouteStopDTO> stops, long mealStopCount) {
        boolean hasComposerMealWindow = stops.stream().anyMatch(stop -> !isBlank(stop.intendedMealWindow()));
        if (hasComposerMealWindow) {
            boolean lunchCovered = stops.stream().anyMatch(stop ->
                    "MEAL".equalsIgnoreCase(stop.routeRole()) && "LUNCH".equalsIgnoreCase(stop.intendedMealWindow())
            );
            boolean dinnerCovered = stops.stream().anyMatch(stop ->
                    "MEAL".equalsIgnoreCase(stop.routeRole()) && "DINNER".equalsIgnoreCase(stop.intendedMealWindow())
            );
            return new MealCoverage(lunchCovered, dinnerCovered);
        }
        boolean dinnerCovered = stops.stream().anyMatch(stop -> this.containsAny(stop.slotLabel(), "晚", "夜"));
        boolean lunchCovered = mealStopCount > 0 && !dinnerCovered;
        if (mealStopCount > 1) {
            lunchCovered = true;
            dinnerCovered = true;
        }
        return new MealCoverage(lunchCovered, dinnerCovered);
    }

    private boolean containsAny(String value, String... tokens) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase();
        for (String token : tokens) {
            if (normalized.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private double avg(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToDouble(row -> doubleValue(row.get(key))).average().orElse(0d);
    }

    private TagAffinityStats profileTagAffinityStats(
            UserPreferenceProfileDTO profile,
            double profileConfidence,
            CandidateRouteDTO route,
            FeatureSource source
    ) {
        if (profile == null || profileConfidence <= 0d || profile.tagAffinities().isEmpty()) {
            return TagAffinityStats.zero();
        }

        Map<String, BigDecimal> normalizedAffinities = com.urbansidequest.backend.handler.route.linear.InterestTagNormalizer
                .normalizedAffinities(profile.tagAffinities(), source.interestTagCatalog());
        double profileAffinitySum = normalizedAffinities.entrySet().stream()
                .filter(entry -> !isBlank(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) > 0)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        if (profileAffinitySum <= 0d) {
            return TagAffinityStats.zero();
        }

        Map<String, Double> profileWeights = new LinkedHashMap<>();
        normalizedAffinities.entrySet().stream()
                .filter(entry -> !isBlank(entry.getKey()))
                .filter(entry -> entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .forEach(entry -> profileWeights.put(entry.getKey(), entry.getValue().doubleValue() / profileAffinitySum));
        if (profileWeights.isEmpty()) {
            return TagAffinityStats.zero();
        }

        Map<String, Integer> routeTagCounts = this.routeTagCounts(route, source);
        int routeTagCountSum = routeTagCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (routeTagCountSum <= 0) {
            return TagAffinityStats.zero();
        }

        Set<String> routeTags = routeTagCounts.keySet();
        double coverage = routeTags.stream()
                .mapToDouble(tag -> profileWeights.getOrDefault(tag, 0d))
                .sum();
        double precision = routeTagCounts.entrySet().stream()
                .filter(entry -> profileWeights.containsKey(entry.getKey()))
                .mapToDouble(entry -> entry.getValue() / (double) routeTagCountSum)
                .sum();

        Set<String> unionTags = new LinkedHashSet<>(profileWeights.keySet());
        unionTags.addAll(routeTags);
        double jaccardNumerator = 0d;
        double jaccardDenominator = 0d;
        for (String tag : unionTags) {
            double profileWeight = profileWeights.getOrDefault(tag, 0d);
            double routeWeight = routeTagCounts.getOrDefault(tag, 0) / (double) routeTagCountSum;
            jaccardNumerator += Math.min(profileWeight, routeWeight);
            jaccardDenominator += Math.max(profileWeight, routeWeight);
        }
        double jaccard = jaccardDenominator <= 0d ? 0d : jaccardNumerator / jaccardDenominator;

        List<String> topTags = normalizedAffinities.entrySet().stream()
                .filter(entry -> !isBlank(entry.getKey()))
                .filter(entry -> entry.getValue() != null && entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.<Map.Entry<String, BigDecimal>, Double>comparing(entry -> entry.getValue().doubleValue()).reversed())
                .limit(TOP_TAG_K)
                .map(Map.Entry::getKey)
                .toList();
        int topTagCount = Math.min(TOP_TAG_K, topTags.size());
        double topTagHitRatio = topTagCount == 0 ? 0d : topTags.stream()
                .filter(routeTags::contains)
                .count() / (double) topTagCount;

        return new TagAffinityStats(
                profileConfidence * clamp01(coverage),
                profileConfidence * clamp01(precision),
                profileConfidence * clamp01(jaccard),
                profileConfidence * clamp01(topTagHitRatio)
        );
    }

    private Map<String, Integer> routeTagCounts(CandidateRouteDTO route, FeatureSource source) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RouteStopDTO stop : route.stops()) {
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiSemanticProfile semantic = source.semanticByPoiId().getOrDefault(poiId, PoiSemanticProfile.empty());
            for (String tag : com.urbansidequest.backend.handler.route.linear.InterestTagNormalizer.normalizedPoiTags(
                    semantic.poiTagHits(),
                    source.interestTagCatalog()
            )) {
                if (!isBlank(tag)) {
                    counts.merge(tag, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private boolean isTransferSegment(Map<String, Object> row) {
        return doubleValue(row.get("transportMode_BUS")) > 0d || doubleValue(row.get("transportMode_SUBWAY")) > 0d;
    }

    private RouteSegmentDTO routeSegmentAt(CandidateRouteDTO route, int index) {
        if (route.segments() == null || index < 0 || index >= route.segments().size()) {
            return null;
        }
        return route.segments().get(index);
    }

    private static double bit(boolean value) {
        return value ? 1d : 0d;
    }

    private static double clamp01(double value) {
        return clamp(value, 0d, 1d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private static double doubleOf(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String writeJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线偏好训练特征序列化失败", exception);
        }
    }

    private record FeatureSource(
            Map<String, PoiCandidateDTO> candidatesByPoiId,
            Map<String, PoiLinearTraceDTO> tracesByPoiId,
            Map<String, PoiSemanticProfile> semanticByPoiId,
            List<InterestTagCatalogPO> interestTagCatalog
    ) {
    }

    private record MealCoverage(boolean lunchCovered, boolean dinnerCovered) {
    }

    private record SegmentFeature(
            int index,
            SegmentTransportMode mode,
            int distanceMeters,
            int durationMinutes,
            boolean missing,
            RouteSegmentSource source
    ) {

        private boolean calibrated() {
            return (this.source == RouteSegmentSource.AMAP_DIRECT || this.source == RouteSegmentSource.AMAP_FALLBACK)
                    && this.distanceMeters > 0;
        }
    }

    private record ModeDistanceStats(
            double walkRatio,
            double bikeRatio,
            double busRatio,
            double subwayRatio,
            double taxiRatio
    ) {

        private static ModeDistanceStats zero() {
            return new ModeDistanceStats(0d, 0d, 0d, 0d, 0d);
        }
    }

    private record TransitFeature(double high, double medium, double low, double distanceNorm) {
    }

    private record BudgetStats(double budgetTotalNorm, double budgetPressure, double missingPriceRatio) {
    }

    private record TagAffinityStats(double coverage, double precision, double jaccard, double topTagHitRatio) {

        private static TagAffinityStats zero() {
            return new TagAffinityStats(0d, 0d, 0d, 0d);
        }
    }
}
