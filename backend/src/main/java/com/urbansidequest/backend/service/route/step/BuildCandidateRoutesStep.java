package com.urbansidequest.backend.service.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(70)
@Component
public class BuildCandidateRoutesStep implements RouteGenerationStep {

    private static final int DEFAULT_STAY_MINUTES = 45;

    @Override
    public void execute(RouteGenerationContext context) {
        List<PoiCandidateDTO> candidates = context.getPoiCandidates();
        if (candidates.isEmpty()) {
            context.setCandidateRoutes(List.of());
            context.addWarning("当前范围内没有可用候选点");
            return;
        }

        List<PoiCandidateDTO> orderedCandidates = this.orderCandidates(candidates);
        List<CandidateRouteDTO> routes = new ArrayList<>();
        routes.add(this.buildRoute("A", "路线 A · 稳妥省心线", orderedCandidates, context));
        routes.add(this.buildRoute("B", "路线 B · 兴趣优先线", this.rotate(orderedCandidates, 1), context));
        routes.add(this.buildRoute("C", "路线 C · 轻量备选线", this.rotate(orderedCandidates, 2), context));
        context.setCandidateRoutes(routes);
    }

    private List<PoiCandidateDTO> orderCandidates(List<PoiCandidateDTO> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(PoiCandidateDTO::mustVisit).reversed()
                        .thenComparing(candidate -> candidate.amapRating() == null ? BigDecimal.ZERO : candidate.amapRating(), Comparator.reverseOrder()))
                .limit(5)
                .toList();
    }

    private List<PoiCandidateDTO> rotate(List<PoiCandidateDTO> candidates, int distance) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        List<PoiCandidateDTO> result = new ArrayList<>(candidates);
        int safeDistance = Math.min(distance, result.size() - 1);
        List<PoiCandidateDTO> tail = new ArrayList<>(result.subList(safeDistance, result.size()));
        tail.addAll(result.subList(0, safeDistance));
        return tail;
    }

    private CandidateRouteDTO buildRoute(
            String routeCode,
            String title,
            List<PoiCandidateDTO> orderedCandidates,
            RouteGenerationContext context
    ) {
        List<PoiCandidateDTO> stops = orderedCandidates.stream().limit(4).toList();
        List<RouteStopDTO> routeStops = new ArrayList<>();
        int totalDuration = 0;
        int totalDistance = 0;
        Integer budgetCent = 0;
        for (int index = 0; index < stops.size(); index++) {
            PoiCandidateDTO current = stops.get(index);
            SegmentCostDTO nextCost = index < stops.size() - 1
                    ? this.findBestCost(current, stops.get(index + 1), context)
                    : null;
            totalDuration += DEFAULT_STAY_MINUTES;
            if (nextCost != null) {
                totalDuration += nextCost.durationMinutes();
                totalDistance += nextCost.distanceMeters();
            }
            if (current.avgPriceCent() != null) {
                budgetCent += current.avgPriceCent();
            }
            routeStops.add(new RouteStopDTO(
                    current.poiId(),
                    index + 1,
                    current.name(),
                    current.category(),
                    current.location(),
                    DEFAULT_STAY_MINUTES,
                    nextCost == null ? null : nextCost.mode(),
                    nextCost == null ? null : nextCost.distanceMeters(),
                    nextCost == null ? null : nextCost.durationMinutes(),
                    current.reasonSeed(),
                    current.mustVisit() ? "必去点优先保证，若营业状态异常需要替换" : null
            ));
        }

        return new CandidateRouteDTO(
                routeCode,
                title,
                "基于当前区域、出行组合和兴趣偏好生成的第一版候选路线",
                totalDuration,
                totalDistance,
                budgetCent == 0 ? null : budgetCent,
                RiskLevel.LOW,
                "当前版本先使用规则生成路线，后续会接入高德 POI 详情和交通成本缓存。",
                routeStops,
                0
        );
    }

    private SegmentCostDTO findBestCost(PoiCandidateDTO origin, PoiCandidateDTO destination, RouteGenerationContext context) {
        return context.getSegmentCosts().stream()
                .filter(cost -> cost.originPoiId().equals(origin.poiId()) && cost.destinationPoiId().equals(destination.poiId()))
                .min(Comparator.comparingInt(SegmentCostDTO::durationMinutes))
                .orElse(null);
    }
}
