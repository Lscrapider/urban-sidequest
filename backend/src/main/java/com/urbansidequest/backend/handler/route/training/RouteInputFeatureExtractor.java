package com.urbansidequest.backend.handler.route.training;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.LinearScoreConstants;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticProfile;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
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

    private static final double STAY_BUDGET_RATIO = 0.85d;

    private static final double SEGMENT_COMFORT_DURATION_MINUTES = 20d;

    private static final double MIN_SEGMENT_COMFORT_DISTANCE_METERS = 500d;

    private static final double LONG_SEGMENT_PRESSURE_THRESHOLD = 1.0d;

    private static final double VISITED_VICINITY_THRESHOLD_METERS = 300d;

    private static final double RISK_COST_THRESHOLD = -0.12d;

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);

    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);

    private static final LocalTime DINNER_START = LocalTime.of(17, 30);

    private static final LocalTime DINNER_END = LocalTime.of(20, 0);

    private final ObjectMapper objectMapper;

    private final PoiSemanticResolver poiSemanticResolver;

    public RouteInputFeatureExtractor(ObjectMapper objectMapper, PoiSemanticResolver poiSemanticResolver) {
        this.objectMapper = objectMapper;
        this.poiSemanticResolver = poiSemanticResolver;
    }

    public RouteInputFeatureSnapshot extract(CandidateRouteDTO route, RouteGenerationContext context) {
        FeatureSource source = this.toFeatureSource(route, context);
        List<Map<String, Object>> stopMatrix = this.stopMatrix(route, source);
        List<Map<String, Object>> segmentMatrix = this.segmentMatrix(route, context, source);
        Map<String, Object> routeDerivedVector = this.routeDerivedVector(route, context, source, stopMatrix, segmentMatrix);
        Map<String, Object> contextCrossVector = this.contextCrossVector(context, routeDerivedVector);
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
            String poiId = this.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiCandidateDTO candidate = candidatesByPoiId.get(poiId);
            semanticByPoiId.put(poiId, candidate == null
                    ? PoiSemanticProfile.empty()
                    : this.poiSemanticResolver.resolve(candidate, context.getPoiSemanticMappings()));
        }
        return new FeatureSource(candidatesByPoiId, tracesByPoiId, semanticByPoiId);
    }

    private List<Map<String, Object>> stopMatrix(CandidateRouteDTO route, FeatureSource source) {
        List<RouteStopDTO> stops = route.stops();
        List<Map<String, Object>> rows = new ArrayList<>();
        int stopCount = stops.size();
        for (int index = 0; index < stopCount; index++) {
            RouteStopDTO stop = stops.get(index);
            String poiId = this.poiIdFromStopId(stop.stopId(), route.routeCode());
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
            row.put("personalizationScore", trace == null ? 0d : trace.personalizationScore());
            row.put("linearScore", trace == null ? 0d : trace.linearScore());
            row.put("poiLinearTraceMissing", bit(trace == null));

            row.put("routeRole_MUST_VISIT", bit(candidate != null && candidate.mustVisit()));
            row.put("routeRole_ANCHOR", bit(index == 0));
            row.put("routeRole_MEAL", bit(this.isMealStop(stop, semantic)));
            row.put("routeRole_REST", bit(this.isRestStop(stop)));
            row.put("routeRole_LOCAL", bit(semantic.local()));
            row.put("routeRole_PHOTO", bit(semantic.photoFriendly()));
            row.put("routeRole_BACKUP", bit(this.containsAny(stop.slotLabel(), "备选", "backup")));

            row.put("stayMinutesNorm", stop.stayMinutes() / 60d);
            row.put("orderPositionNorm", stopCount <= 1 ? 0d : index / (double) (stopCount - 1));
            row.put("isFirstStop", bit(index == 0));
            row.put("isLastStop", bit(index == stopCount - 1));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> segmentMatrix(CandidateRouteDTO route, RouteGenerationContext context, FeatureSource source) {
        List<RouteStopDTO> stops = route.stops();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (stops.size() < 2) {
            return rows;
        }
        double routeComfortDistance = this.routeComfortDistanceMeters(context);
        double segmentComfortDistance = Math.max(MIN_SEGMENT_COMFORT_DISTANCE_METERS, routeComfortDistance / Math.max(1, stops.size() - 1));
        for (int index = 0; index < stops.size() - 1; index++) {
            RouteStopDTO origin = stops.get(index);
            RouteStopDTO destination = stops.get(index + 1);
            boolean missing = origin.location() == null || destination.location() == null;
            int distanceMeters = missing ? 0 : GeoMath.distanceMeters(origin.location(), destination.location());
            SegmentTransportMode mode = this.resolveSegmentMode(origin.transportToNext(), context.getGenerateParam().getTransportProfile(), source, origin, destination, route.routeCode());
            int durationMinutes = missing ? 0 : this.estimateDurationMinutes(distanceMeters, mode);
            double distancePressure = missing ? 1d : clamp(distanceMeters / segmentComfortDistance, 0d, 2d);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("straightDistanceNorm", missing ? 1d : distanceMeters / segmentComfortDistance);
            row.put("estimatedDurationNorm", missing ? 1d : durationMinutes / SEGMENT_COMFORT_DURATION_MINUTES);
            row.put("transportMode_WALK", bit(mode == SegmentTransportMode.WALK));
            row.put("transportMode_BIKE", bit(mode == SegmentTransportMode.BIKE));
            row.put("transportMode_BUS", bit(mode == SegmentTransportMode.BUS));
            row.put("transportMode_SUBWAY", bit(mode == SegmentTransportMode.SUBWAY));
            row.put("transportMode_TAXI", bit(mode == SegmentTransportMode.TAXI));
            row.put("isBacktracking", bit(!missing && this.isBacktracking(stops, index)));
            row.put("distancePressure", distancePressure);
            row.put("segmentEstimateMissing", bit(missing));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> routeDerivedVector(
            CandidateRouteDTO route,
            RouteGenerationContext context,
            FeatureSource source,
            List<Map<String, Object>> stopMatrix,
            List<Map<String, Object>> segmentMatrix
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

        boolean requiresLunch = this.overlapsMealWindow(context.getGenerateParam().getDepartureTime(), durationMinutes, LUNCH_START, LUNCH_END);
        boolean requiresDinner = this.overlapsMealWindow(context.getGenerateParam().getDepartureTime(), durationMinutes, DINNER_START, DINNER_END);
        long mealStopCount = stopMatrix.stream().filter(row -> doubleValue(row.get("routeRole_MEAL")) > 0d).count();
        long restStopCount = stopMatrix.stream().filter(row -> doubleValue(row.get("routeRole_REST")) > 0d).count();
        boolean dinnerCovered = stops.stream().anyMatch(stop -> this.containsAny(stop.slotLabel(), "晚", "夜"));
        boolean lunchCovered = mealStopCount > 0 && !dinnerCovered;
        if (mealStopCount > 1) {
            lunchCovered = true;
            dinnerCovered = true;
        }
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

        vector.put("interestCoverageRatio", this.interestCoverageRatio(context, route, source));
        vector.put("localStopRatio", this.avg(stopMatrix, "isLocal"));
        vector.put("classicStopRatio", this.avg(stopMatrix, "isClassic"));
        vector.put("photoFriendlyStopRatio", this.avg(stopMatrix, "isPhotoFriendly"));
        vector.put("nightFriendlyStopRatio", this.avg(stopMatrix, "isNightFriendly"));
        vector.put("quietStopRatio", this.avg(stopMatrix, "isQuiet"));
        vector.put("hiddenGemStopRatio", this.avg(stopMatrix, "isHiddenGem"));

        List<String> categoryGroups = this.categoryGroups(route, source);
        vector.put("categoryDiversityRatio", stopCount == 0 ? 0d : new LinkedHashSet<>(categoryGroups).size() / (double) stopCount);
        vector.put("dominantCategoryRatio", stopCount == 0 ? 0d : this.dominantCategoryCount(categoryGroups) / (double) stopCount);
        vector.put("consecutiveSameCategoryMaxNorm", stopCount == 0 ? 0d : this.maxConsecutiveCategoryCount(categoryGroups) / (double) stopCount);

        BudgetStats budgetStats = this.budgetStats(route, context, source);
        vector.put("budgetTotalNorm", budgetStats.budgetTotalNorm());
        vector.put("budgetPressure", budgetStats.budgetPressure());
        vector.put("missingPriceRatio", budgetStats.missingPriceRatio());

        vector.put("avgPoiLinearScore", this.avg(stopMatrix, "linearScore"));
        vector.put("minPoiLinearScore", stopMatrix.stream().mapToDouble(row -> doubleValue(row.get("linearScore"))).min().orElse(0d));
        vector.put("avgInterestScore", this.avg(stopMatrix, "interestScore"));
        vector.put("avgGoalScore", this.avg(stopMatrix, "goalScore"));
        vector.put("avgQualityScore", this.avg(stopMatrix, "qualityScore"));
        vector.put("avgPersonalizationScore", this.avg(stopMatrix, "personalizationScore"));
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
        contextJson.put("departureTime", context.getGenerateParam().getDepartureTime());
        contextJson.put("durationMinutes", context.getGenerateParam().getDurationMinutes());
        contextJson.put("routeTimeStructure", timeStructure);
        contextJson.put("weather", context.getRouteWeather());
        contextJson.put("userPreferenceProfile", context.getUserPreferenceProfile());
        return contextJson;
    }

    private Map<String, Object> contextCrossVector(
            RouteGenerationContext context,
            Map<String, Object> routeDerivedVector
    ) {
        Map<String, Object> vector = new LinkedHashMap<>();
        UserPreferenceProfileDTO profile = context.getUserPreferenceProfile();
        double profileConfidence = profile == null ? 0d : doubleOf(profile.profileConfidence());
        double distanceSensitivity = profile == null ? 0d : doubleOf(profile.distanceSensitivity());
        double budgetSensitivity = profile == null ? 0d : doubleOf(profile.budgetSensitivity());
        double hiddenGemAffinity = profile == null ? 0d : doubleOf(profile.hiddenGemAffinity());

        double totalDistanceNorm = doubleValue(routeDerivedVector.get("totalDistanceNorm"));
        double maxSegmentDistanceNorm = doubleValue(routeDerivedVector.get("maxSegmentDistanceNorm"));
        double budgetPressure = doubleValue(routeDerivedVector.get("budgetPressure"));
        double hiddenGemStopRatio = doubleValue(routeDerivedVector.get("hiddenGemStopRatio"));
        double avgPersonalizationScore = doubleValue(routeDerivedVector.get("avgPersonalizationScore"));
        double localStopRatio = doubleValue(routeDerivedVector.get("localStopRatio"));
        double classicStopRatio = doubleValue(routeDerivedVector.get("classicStopRatio"));
        double photoFriendlyStopRatio = doubleValue(routeDerivedVector.get("photoFriendlyStopRatio"));
        double nightFriendlyStopRatio = doubleValue(routeDerivedVector.get("nightFriendlyStopRatio"));
        double highRiskStopRatio = doubleValue(routeDerivedVector.get("highRiskStopRatio"));
        double requiresLunchFlag = doubleValue(routeDerivedVector.get("requiresLunchFlag"));
        double requiresDinnerFlag = doubleValue(routeDerivedVector.get("requiresDinnerFlag"));
        double lunchCoveredFlag = doubleValue(routeDerivedVector.get("lunchCoveredFlag"));
        double dinnerCoveredFlag = doubleValue(routeDerivedVector.get("dinnerCoveredFlag"));

        vector.put("profileDistanceTotalPressure", profileConfidence * distanceSensitivity * totalDistanceNorm);
        vector.put("profileDistanceMaxSegmentPressure", profileConfidence * distanceSensitivity * maxSegmentDistanceNorm);
        vector.put("profileBudgetPressure", profileConfidence * budgetSensitivity * budgetPressure);
        vector.put("profileHiddenGemMatch", profileConfidence * hiddenGemAffinity * hiddenGemStopRatio);
        vector.put("profilePersonalizationAvg", avgPersonalizationScore);

        RouteGoal routeGoal = context.getGenerateParam().getRouteGoal();
        vector.put("goalLocalMatch", bit(RouteGoal.LOCAL == routeGoal) * localStopRatio);
        vector.put("goalClassicMatch", bit(RouteGoal.CLASSIC == routeGoal) * classicStopRatio);
        vector.put("goalPhotoMatch", bit(RouteGoal.PHOTO == routeGoal) * photoFriendlyStopRatio);
        vector.put("goalNightMatch", bit(RouteGoal.NIGHT == routeGoal) * nightFriendlyStopRatio);
        vector.put("goalLowBudgetMismatch", bit(RouteGoal.LOW_BUDGET == routeGoal) * budgetPressure);
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
            String poiId = this.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiCandidateDTO candidate = source.candidatesByPoiId().get(poiId);
            if (candidate != null) {
                hits.addAll(candidate.matchedInterestTags());
            }
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
            String poiId = this.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiCandidateDTO candidate = source.candidatesByPoiId().get(poiId);
            PoiSemanticProfile semantic = source.semanticByPoiId().getOrDefault(poiId, PoiSemanticProfile.empty());
            boolean relevant = semantic.isConsumable() || this.isMealStop(stop, semantic) || this.isRestStop(stop);
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

    private List<String> categoryGroups(CandidateRouteDTO route, FeatureSource source) {
        List<String> groups = new ArrayList<>();
        for (RouteStopDTO stop : route.stops()) {
            String poiId = this.poiIdFromStopId(stop.stopId(), route.routeCode());
            PoiSemanticProfile semantic = source.semanticByPoiId().getOrDefault(poiId, PoiSemanticProfile.empty());
            groups.add(semantic.categoryGroups().stream()
                    .sorted()
                    .findFirst()
                    .orElse(stop.category() == null ? "UNKNOWN" : stop.category()));
        }
        return groups;
    }

    private int dominantCategoryCount(List<String> categoryGroups) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String group : categoryGroups) {
            counts.merge(group, 1, Integer::sum);
        }
        return counts.values().stream().max(Comparator.naturalOrder()).orElse(0);
    }

    private int maxConsecutiveCategoryCount(List<String> categoryGroups) {
        int max = 0;
        int current = 0;
        String previous = null;
        for (String group : categoryGroups) {
            if (group.equals(previous)) {
                current++;
            } else {
                current = 1;
                previous = group;
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

    private SegmentTransportMode resolveSegmentMode(
            SegmentTransportMode rawMode,
            TransportProfile profile,
            FeatureSource source,
            RouteStopDTO origin,
            RouteStopDTO destination,
            String routeCode
    ) {
        SegmentTransportMode mode = rawMode == null ? this.defaultMode(profile) : rawMode;
        if (mode == SegmentTransportMode.DRIVE) {
            return SegmentTransportMode.TAXI;
        }
        if (mode != SegmentTransportMode.TRANSIT) {
            return mode;
        }
        if (profile == TransportProfile.WALK_BUS || profile == TransportProfile.WALK_TRANSIT) {
            return SegmentTransportMode.BUS;
        }
        if (profile == TransportProfile.WALK_SUBWAY || profile == TransportProfile.BIKE_SUBWAY) {
            return SegmentTransportMode.SUBWAY;
        }
        return this.defaultMode(profile);
    }

    private SegmentTransportMode defaultMode(TransportProfile profile) {
        if (profile == null || profile.getAllowedSegmentModes().isEmpty()) {
            return SegmentTransportMode.WALK;
        }
        SegmentTransportMode mode = profile.getAllowedSegmentModes().get(0);
        if (mode == SegmentTransportMode.TRANSIT) {
            return profile == TransportProfile.BIKE_SUBWAY ? SegmentTransportMode.SUBWAY : SegmentTransportMode.BUS;
        }
        if (mode == SegmentTransportMode.DRIVE) {
            return SegmentTransportMode.TAXI;
        }
        return mode;
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

    private boolean overlapsMealWindow(Instant departureTime, int durationMinutes, LocalTime start, LocalTime end) {
        if (departureTime == null) {
            return false;
        }
        LocalTime routeStart = departureTime.atZone(CHINA_ZONE).toLocalTime();
        LocalTime routeEnd = departureTime.plusSeconds(durationMinutes * 60L).atZone(CHINA_ZONE).toLocalTime();
        return !routeEnd.isBefore(start) && !routeStart.isAfter(end);
    }

    private double routeComfortDistanceMeters(RouteGenerationContext context) {
        TransportProfile profile = context.getGenerateParam().getTransportProfile();
        DurationBucket bucket = DurationBucket.fromMinutes(context.getGenerateParam().getDurationMinutes());
        return profile.defaultRadiusMeters(bucket);
    }

    private double budgetCapCent(BudgetLevel budgetLevel) {
        return switch (budgetLevel) {
            case LOW -> 10000d;
            case NORMAL -> LinearScoreConstants.BUDGET_CAP_CENT;
            case FLEXIBLE -> 30000d;
        };
    }

    private boolean isMealStop(RouteStopDTO stop, PoiSemanticProfile semantic) {
        return semantic.isMealCandidate() || this.containsAny(stop.slotLabel(), "餐", "饭", "美食")
                || this.containsAny(stop.category(), "FOOD");
    }

    private boolean isRestStop(RouteStopDTO stop) {
        return this.containsAny(stop.slotLabel(), "休息", "咖啡") || this.containsAny(stop.category(), "REST");
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

    private String poiIdFromStopId(String stopId, String routeCode) {
        if (stopId == null || routeCode == null) {
            return stopId;
        }
        String suffix = "-" + routeCode;
        return stopId.endsWith(suffix) ? stopId.substring(0, stopId.length() - suffix.length()) : stopId;
    }

    private double avg(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToDouble(row -> doubleValue(row.get(key))).average().orElse(0d);
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
            Map<String, PoiSemanticProfile> semanticByPoiId
    ) {
    }

    private record TransitFeature(double high, double medium, double low, double distanceNorm) {
    }

    private record BudgetStats(double budgetTotalNorm, double budgetPressure, double missingPriceRatio) {
    }
}
