package com.urbansidequest.backend.handler.route.search;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.segment.SegmentCostStrategy;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BeamSearchRouteSelector {

    private static final int DEFAULT_STAY_MINUTES = 45;

    private static final int MIN_STOPS = 3;

    private static final int MAX_STOPS = 5;

    private static final int BEAM_WIDTH = 24;

    private static final int ROUTE_COUNT = 3;

    private static final int SIMILARITY_PENALTY = 600;

    private static final int MAX_RECOMMENDED_OVERLAP = 1;

    private static final int RELAXED_RECOMMENDED_OVERLAP = 2;

    private final List<SegmentCostStrategy> segmentCostStrategies;

    public BeamSearchRouteSelector(List<SegmentCostStrategy> segmentCostStrategies) {
        this.segmentCostStrategies = segmentCostStrategies;
    }

    public List<CandidateRouteDTO> selectRoutes(RouteGenerationContext context) {
        List<RouteSearchState> completedStates = this.search(context);
        if (completedStates.isEmpty()) {
            context.addWarning("没有路线完全满足搜索约束，已返回候选点兜底路线");
            completedStates = List.of(this.fallbackState(context));
        }

        List<RouteSearchState> selectedStates = this.selectDiverseStates(completedStates, context);
        List<CandidateRouteDTO> routes = new ArrayList<>();
        for (int index = 0; index < selectedStates.size(); index++) {
            routes.add(this.toCandidateRoute(selectedStates.get(index), context, index));
        }
        return routes;
    }

    private List<RouteSearchState> search(RouteGenerationContext context) {
        RouteSearchState initialState = new RouteSearchState(List.of(), Set.of(), Set.of(), 0, 0, 0, 0);
        List<RouteSearchState> beam = List.of(initialState);
        List<RouteSearchState> completedStates = new ArrayList<>();

        for (int depth = 0; depth < MAX_STOPS; depth++) {
            List<RouteSearchState> expandedStates = new ArrayList<>();
            for (RouteSearchState state : beam) {
                for (PoiCandidateDTO candidate : context.getPoiCandidates()) {
                    if (state.poiIds().contains(candidate.poiId())) {
                        continue;
                    }
                    RouteSearchState nextState = this.appendStop(state, candidate, context);
                    if (nextState.totalDurationMinutes() <= context.getGenerateParam().getDurationMinutes()) {
                        expandedStates.add(nextState);
                        if (nextState.stops().size() >= MIN_STOPS) {
                            completedStates.add(nextState);
                        }
                    }
                }
            }
            if (expandedStates.isEmpty()) {
                break;
            }
            beam = expandedStates.stream()
                    .sorted(Comparator.comparingInt(RouteSearchState::score).reversed())
                    .limit(BEAM_WIDTH)
                    .toList();
        }

        List<RouteSearchState> mustVisitCoveredStates = completedStates.stream()
                .filter(state -> this.coversMustVisit(state, context))
                .toList();
        if (!mustVisitCoveredStates.isEmpty()) {
            return mustVisitCoveredStates.stream()
                    .sorted(Comparator.comparingInt(RouteSearchState::score).reversed())
                    .toList();
        }
        context.addWarning("必去点数量或时长约束导致无法全部安排，已返回最接近路线");
        return completedStates.stream()
                .sorted(Comparator.comparingInt(RouteSearchState::score).reversed())
                .toList();
    }

    private RouteSearchState appendStop(
            RouteSearchState state,
            PoiCandidateDTO candidate,
            RouteGenerationContext context
    ) {
        SegmentCostDTO transitionCost = state.stops().isEmpty()
                ? null
                : this.findBestCost(state.stops().get(state.stops().size() - 1), candidate, context);
        int duration = state.totalDurationMinutes() + DEFAULT_STAY_MINUTES;
        int distance = state.totalDistanceMeters();
        if (transitionCost != null) {
            duration += transitionCost.durationMinutes();
            distance += transitionCost.distanceMeters();
        }
        int budget = state.budgetCent() + (candidate.avgPriceCent() == null ? 0 : candidate.avgPriceCent());
        List<PoiCandidateDTO> stops = new ArrayList<>(state.stops());
        stops.add(candidate);
        Set<String> poiIds = new LinkedHashSet<>(state.poiIds());
        poiIds.add(candidate.poiId());
        Set<String> coveredInterestTags = new LinkedHashSet<>(state.coveredInterestTags());
        coveredInterestTags.addAll(candidate.matchedInterestTags());
        int score = this.score(stops, coveredInterestTags, duration, distance, budget, context);
        return new RouteSearchState(stops, poiIds, coveredInterestTags, duration, distance, budget, score);
    }

    private int score(
            List<PoiCandidateDTO> stops,
            Set<String> coveredInterestTags,
            int duration,
            int distance,
            int budget,
            RouteGenerationContext context
    ) {
        int score = 0;
        score += (int) stops.stream().filter(PoiCandidateDTO::mustVisit).count() * 10000;
        score += this.coveredUserInterestCount(coveredInterestTags, context) * 1800;
        score += Math.min(this.mealCount(stops), this.resolveMealNeed(context)) * 450;
        score += Math.min(this.restCount(stops), this.resolveRestNeed(context)) * 320;
        score += stops.size() * 120;
        score += this.poiQualityScore(stops);
        score += this.routeGoalScore(stops, context);
        score -= Math.abs(context.getGenerateParam().getDurationMinutes() - duration);
        score -= distance / 80;
        score -= this.duplicateCategoryPenalty(stops);
        score -= (int) stops.stream().filter(candidate -> PoiCandidateRole.BACKUP == candidate.role()).count() * 120;
        if (budget > 0 && "LOW_BUDGET".equals(context.getGenerateParam().getRouteGoal().name())) {
            score -= budget / 1000;
        }
        return score;
    }

    private int poiQualityScore(List<PoiCandidateDTO> stops) {
        return stops.stream()
                .map(PoiCandidateDTO::amapRating)
                .filter(rating -> rating != null)
                .mapToInt(rating -> rating.multiply(BigDecimal.TEN).intValue())
                .sum();
    }

    private int routeGoalScore(List<PoiCandidateDTO> stops, RouteGenerationContext context) {
        return switch (context.getGenerateParam().getRouteGoal()) {
            case STEADY -> -this.totalRoleCount(stops, PoiCandidateRole.BACKUP) * 80;
            case CLASSIC -> this.categoryCount(stops, "SCENIC") * 180 + this.categoryCount(stops, "CULTURE") * 120;
            case LOCAL -> this.totalRoleCount(stops, PoiCandidateRole.LOCAL) * 220;
            case LOW_BUDGET -> stops.stream().anyMatch(stop -> stop.avgPriceCent() == null) ? 120 : 0;
            case NIGHT -> this.categoryCount(stops, "NIGHT") * 240;
            case PHOTO -> this.categoryCount(stops, "SCENIC") * 160;
        };
    }

    private int duplicateCategoryPenalty(List<PoiCandidateDTO> stops) {
        Set<String> categories = new HashSet<>();
        int duplicateCount = 0;
        for (PoiCandidateDTO stop : stops) {
            if (!categories.add(stop.category())) {
                duplicateCount++;
            }
        }
        return duplicateCount * 180;
    }

    private List<RouteSearchState> selectDiverseStates(List<RouteSearchState> states, RouteGenerationContext context) {
        List<RouteSearchState> selectedStates = new ArrayList<>();
        List<RouteSearchState> candidates = states.stream()
                .sorted(Comparator.comparingInt(RouteSearchState::score).reversed())
                .toList();
        boolean overlapRelaxed = false;
        while (selectedStates.size() < ROUTE_COUNT && !candidates.isEmpty()) {
            List<RouteSearchState> eligibleCandidates = this.filterByRecommendedOverlap(
                    candidates,
                    selectedStates,
                    MAX_RECOMMENDED_OVERLAP
            );
            if (eligibleCandidates.isEmpty() && !selectedStates.isEmpty()) {
                eligibleCandidates = this.filterByRecommendedOverlap(
                        candidates,
                        selectedStates,
                        RELAXED_RECOMMENDED_OVERLAP
                );
                if (!eligibleCandidates.isEmpty() && !overlapRelaxed) {
                    context.addWarning("候选点数量不足，部分备选路线的系统推荐点存在 2 个重叠");
                    overlapRelaxed = true;
                }
            }
            if (eligibleCandidates.isEmpty()) {
                eligibleCandidates = candidates;
                if (!selectedStates.isEmpty() && !overlapRelaxed) {
                    context.addWarning("候选点数量不足，备选路线存在较多重叠点");
                    overlapRelaxed = true;
                }
            }
            RouteSearchState bestState = eligibleCandidates.stream()
                    .max(Comparator.comparingInt(state -> this.diversityAdjustedScore(state, selectedStates)))
                    .orElseThrow();
            selectedStates.add(bestState);
            candidates = candidates.stream()
                    .filter(state -> state != bestState)
                    .toList();
        }
        return selectedStates;
    }

    private List<RouteSearchState> filterByRecommendedOverlap(
            List<RouteSearchState> candidates,
            List<RouteSearchState> selectedStates,
            int overlapLimit
    ) {
        if (selectedStates.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> selectedStates.stream()
                        .allMatch(selectedState -> this.recommendedOverlapCount(candidate, selectedState) <= overlapLimit))
                .toList();
    }

    private int diversityAdjustedScore(RouteSearchState state, List<RouteSearchState> selectedStates) {
        int score = state.score();
        for (RouteSearchState selectedState : selectedStates) {
            score -= this.overlapCount(state, selectedState) * SIMILARITY_PENALTY;
        }
        return score;
    }

    private int overlapCount(RouteSearchState left, RouteSearchState right) {
        int count = 0;
        for (String poiId : left.poiIds()) {
            if (right.poiIds().contains(poiId)) {
                count++;
            }
        }
        return count;
    }

    private int recommendedOverlapCount(RouteSearchState left, RouteSearchState right) {
        Set<String> rightRecommendedPoiIds = new HashSet<>();
        for (PoiCandidateDTO stop : right.stops()) {
            if (!stop.mustVisit()) {
                rightRecommendedPoiIds.add(stop.poiId());
            }
        }
        int count = 0;
        for (PoiCandidateDTO stop : left.stops()) {
            if (!stop.mustVisit() && rightRecommendedPoiIds.contains(stop.poiId())) {
                count++;
            }
        }
        return count;
    }

    private CandidateRouteDTO toCandidateRoute(RouteSearchState state, RouteGenerationContext context, int routeIndex) {
        String routeCode = switch (routeIndex) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
        };
        List<RouteStopDTO> routeStops = new ArrayList<>();
        for (int index = 0; index < state.stops().size(); index++) {
            PoiCandidateDTO current = state.stops().get(index);
            SegmentCostDTO nextCost = index < state.stops().size() - 1
                    ? this.findBestCost(current, state.stops().get(index + 1), context)
                    : null;
            routeStops.add(new RouteStopDTO(
                    current.poiId() + "-" + routeCode,
                    index + 1,
                    current.name(),
                    this.slotLabel(current),
                    current.category(),
                    current.location(),
                    current.amapRating(),
                    DEFAULT_STAY_MINUTES,
                    nextCost == null ? null : nextCost.mode(),
                    nextCost == null ? null : nextCost.distanceMeters(),
                    nextCost == null ? null : nextCost.durationMinutes(),
                    this.stopDescription(current),
                    current.imageUrls(),
                    current.reasonSeed(),
                    current.mustVisit() ? "必去点优先保证，若营业状态异常需要替换" : null
            ));
        }
        return new CandidateRouteDTO(
                routeCode,
                this.routeTitle(routeCode),
                "基于高德候选池、本地交通成本和用户兴趣生成的候选路线",
                state.totalDurationMinutes(),
                state.totalDistanceMeters(),
                state.budgetCent() == 0 ? null : state.budgetCent(),
                RiskLevel.LOW,
                "优先覆盖必去点和用户选择兴趣，饭点与休息点按路线时长动态补充。",
                routeStops,
                state.score()
        );
    }

    private String routeTitle(String routeCode) {
        return switch (routeCode) {
            case "A" -> "路线 A · 兴趣优先线";
            case "B" -> "路线 B · 节奏平衡线";
            default -> "路线 C · 轻量备选线";
        };
    }

    private String slotLabel(PoiCandidateDTO candidate) {
        return switch (candidate.role()) {
            case MUST_VISIT -> "必去点";
            case MEAL -> "餐饮";
            case REST -> "休息";
            case LOCAL -> "本地体验";
            case BACKUP -> "备选";
            case ANCHOR -> this.categoryLabel(candidate.category());
        };
    }

    private String categoryLabel(String category) {
        if ("CULTURE".equals(category)) {
            return "文化展馆";
        }
        if ("SCENIC".equals(category)) {
            return "景点";
        }
        if ("FOOD".equals(category)) {
            return "餐饮";
        }
        if ("REST".equals(category)) {
            return "休息";
        }
        if ("LOCAL".equals(category)) {
            return "本地街区";
        }
        if ("NIGHT".equals(category)) {
            return "夜游点";
        }
        if ("MUST_VISIT".equals(category)) {
            return "必去点";
        }
        return "地点";
    }

    private String stopDescription(PoiCandidateDTO candidate) {
        if (candidate.description() != null && !candidate.description().isBlank()) {
            return candidate.description();
        }
        return candidate.name() + "适合作为本条路线的" + this.slotLabel(candidate) + "，" + candidate.reasonSeed() + "。";
    }

    private SegmentCostDTO findBestCost(PoiCandidateDTO origin, PoiCandidateDTO destination, RouteGenerationContext context) {
        return context.getSegmentCosts().stream()
                .filter(cost -> cost.originPoiId().equals(origin.poiId()) && cost.destinationPoiId().equals(destination.poiId()))
                .min(Comparator.comparingInt(SegmentCostDTO::durationMinutes))
                .orElseGet(() -> this.calculateFallbackCost(origin, destination, context));
    }

    private SegmentCostDTO calculateFallbackCost(
            PoiCandidateDTO origin,
            PoiCandidateDTO destination,
            RouteGenerationContext context
    ) {
        return context.getGenerateParam().getTransportProfile().getAllowedSegmentModes().stream()
                .flatMap(mode -> this.segmentCostStrategies.stream()
                        .filter(strategy -> strategy.supports(mode))
                        .findFirst()
                        .map(strategy -> strategy.calculate(origin, destination, context))
                        .stream())
                .min(Comparator.comparingInt(SegmentCostDTO::durationMinutes))
                .orElseGet(() -> new SegmentCostDTO(
                        origin.poiId(),
                        destination.poiId(),
                        context.getGenerateParam().getTransportProfile().getAllowedSegmentModes().get(0),
                        GeoMath.distanceMeters(origin.location(), destination.location()),
                        1,
                        0,
                        0,
                        "本地估算约 1 分钟"
                ));
    }

    private RouteSearchState fallbackState(RouteGenerationContext context) {
        List<PoiCandidateDTO> stops = context.getPoiCandidates().stream()
                .sorted(Comparator.comparing(PoiCandidateDTO::mustVisit).reversed())
                .limit(MAX_STOPS)
                .toList();
        RouteSearchState state = new RouteSearchState(List.of(), Set.of(), Set.of(), 0, 0, 0, 0);
        for (PoiCandidateDTO stop : stops) {
            state = this.appendStop(state, stop, context);
        }
        return state;
    }

    private boolean coversMustVisit(RouteSearchState state, RouteGenerationContext context) {
        Set<String> stopNames = new HashSet<>();
        for (PoiCandidateDTO stop : state.stops()) {
            stopNames.add(stop.name());
        }
        return context.getGenerateParam().getMustVisitPoints().stream()
                .allMatch(mustVisitPoint -> stopNames.contains(mustVisitPoint.getName()));
    }

    private int coveredUserInterestCount(Set<String> coveredInterestTags, RouteGenerationContext context) {
        int count = 0;
        for (String interestTag : context.getGenerateParam().getInterestTags()) {
            if (coveredInterestTags.contains(interestTag)) {
                count++;
            }
        }
        return count;
    }

    private int resolveMealNeed(RouteGenerationContext context) {
        int need = 0;
        if (this.overlapsMealWindow(context, 11, 30, 13, 30)) {
            need++;
        }
        if (this.overlapsMealWindow(context, 17, 30, 20, 0)) {
            need++;
        }
        return need;
    }

    private boolean overlapsMealWindow(
            RouteGenerationContext context,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        java.time.LocalDateTime routeStart = java.time.LocalDateTime.ofInstant(
                context.getGenerateParam().getDepartureTime(),
                java.time.ZoneId.of("Asia/Shanghai")
        );
        java.time.LocalDateTime routeEnd = routeStart.plusMinutes(context.getGenerateParam().getDurationMinutes());
        java.time.LocalDate cursorDate = routeStart.toLocalDate();
        while (!cursorDate.isAfter(routeEnd.toLocalDate())) {
            java.time.LocalDateTime candidateStart = java.time.LocalDateTime.of(
                    cursorDate,
                    java.time.LocalTime.of(startHour, startMinute)
            );
            java.time.LocalDateTime candidateEnd = java.time.LocalDateTime.of(
                    cursorDate,
                    java.time.LocalTime.of(endHour, endMinute)
            );
            if (routeStart.isBefore(candidateEnd) && routeEnd.isAfter(candidateStart)) {
                return true;
            }
            cursorDate = cursorDate.plusDays(1);
        }
        return false;
    }

    private int resolveRestNeed(RouteGenerationContext context) {
        int durationMinutes = context.getGenerateParam().getDurationMinutes();
        if (durationMinutes <= 180) {
            return 0;
        }
        if (durationMinutes <= 360) {
            return 1;
        }
        return 2;
    }

    private int mealCount(List<PoiCandidateDTO> stops) {
        return this.totalRoleCount(stops, PoiCandidateRole.MEAL);
    }

    private int restCount(List<PoiCandidateDTO> stops) {
        return this.totalRoleCount(stops, PoiCandidateRole.REST);
    }

    private int totalRoleCount(List<PoiCandidateDTO> stops, PoiCandidateRole role) {
        return (int) stops.stream().filter(stop -> role == stop.role()).count();
    }

    private int categoryCount(List<PoiCandidateDTO> stops, String category) {
        return (int) stops.stream().filter(stop -> category.equals(stop.category())).count();
    }
}
