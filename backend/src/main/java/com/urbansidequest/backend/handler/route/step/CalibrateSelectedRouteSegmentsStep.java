package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.api.amap.AmapRoutePlanningApi;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RoutePlanDTO;
import com.urbansidequest.backend.domain.dto.RouteSegmentDTO;
import com.urbansidequest.backend.domain.dto.RouteStepDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.manage.RouteSegmentCostCacheManage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CalibrateSelectedRouteSegmentsStep implements RouteGenerationStep {

    private final AmapRoutePlanningApi amapRoutePlanningApi;

    private final RouteSegmentCostCacheManage routeSegmentCostCacheManage;

    public CalibrateSelectedRouteSegmentsStep(
            AmapRoutePlanningApi amapRoutePlanningApi,
            RouteSegmentCostCacheManage routeSegmentCostCacheManage
    ) {
        this.amapRoutePlanningApi = amapRoutePlanningApi;
        this.routeSegmentCostCacheManage = routeSegmentCostCacheManage;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getSelectedRoutes().isEmpty()) {
            return;
        }
        context.setSelectedRoutes(context.getSelectedRoutes().stream()
                .map(route -> this.calibrateRoute(route, context))
                .toList());
    }

    private CandidateRouteDTO calibrateRoute(CandidateRouteDTO route, RouteGenerationContext context) {
        List<RouteStopDTO> stops = route.stops();
        if (stops.size() < 2) {
            return route;
        }
        List<RouteStopDTO> calibratedStops = new ArrayList<>();
        List<RouteSegmentDTO> segments = new ArrayList<>();
        for (int index = 0; index < stops.size(); index++) {
            RouteStopDTO current = stops.get(index);
            if (index == stops.size() - 1) {
                calibratedStops.add(current);
                continue;
            }
            RouteStopDTO next = stops.get(index + 1);
            SegmentTransportMode mode = current.transportToNext() == null
                    ? context.getGenerateParam().getTransportProfile().getAllowedSegmentModes().get(0)
                    : current.transportToNext();
            RouteSegmentDTO segment = this.resolveSegment(
                    route.routeCode(),
                    index + 1,
                    current,
                    next,
                    mode,
                    context
            );
            segments.add(segment);
            calibratedStops.add(this.withSegmentCost(current, segment));
        }

        int calibratedDurationMinutes = calibratedStops.stream().mapToInt(RouteStopDTO::stayMinutes).sum()
                + segments.stream().mapToInt(RouteSegmentDTO::durationMinutes).sum();
        int calibratedDistanceMeters = segments.stream().mapToInt(RouteSegmentDTO::distanceMeters).sum();
        if (calibratedDurationMinutes > context.getGenerateParam().getDurationMinutes()) {
            context.addWarning(route.routeCode() + " 线真实路径校准后超过预设时长，请执行前留意时间余量");
        }
        return new CandidateRouteDTO(
                route.routeCode(),
                route.title(),
                route.summary(),
                calibratedDurationMinutes,
                calibratedDistanceMeters,
                route.budgetCent(),
                route.riskLevel() == null ? RiskLevel.LOW : route.riskLevel(),
                route.explanation(),
                calibratedStops,
                segments,
                route.score()
        );
    }

    private RouteSegmentDTO resolveSegment(
            String routeCode,
            int order,
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode,
            RouteGenerationContext context
    ) {
        return this.findCachedPlan(origin, destination, mode)
                .or(() -> this.fetchAndCachePlan(origin, destination, mode, context))
                .map(plan -> this.toRouteSegment(order, origin, destination, mode, plan))
                .orElseGet(() -> {
                    context.addWarning(routeCode + " 线第 " + order + " 段路径规划失败，已使用本地估算");
                    return this.fallbackSegment(order, origin, destination, mode);
                });
    }

    private java.util.Optional<RoutePlanDTO> findCachedPlan(
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode
    ) {
        return this.routeSegmentCostCacheManage.findLatestRawPayload(origin.location(), destination.location(), mode)
                .flatMap(rawPayload -> this.amapRoutePlanningApi.parseCachedPlan(rawPayload, mode));
    }

    private java.util.Optional<RoutePlanDTO> fetchAndCachePlan(
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode,
            RouteGenerationContext context
    ) {
        java.util.Optional<RoutePlanDTO> plan = this.amapRoutePlanningApi.plan(
                origin.location(),
                destination.location(),
                mode,
                context.getGenerateParam().getRouteCityName(),
                context.getGenerateParam().getRouteCityAdcode()
        );
        plan.ifPresent(routePlan -> this.routeSegmentCostCacheManage.saveRawPayload(
                origin.location(),
                destination.location(),
                mode,
                routePlan
        ));
        return plan;
    }

    private RouteSegmentDTO toRouteSegment(
            int order,
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode,
            RoutePlanDTO plan
    ) {
        return new RouteSegmentDTO(
                order,
                origin.stopId(),
                destination.stopId(),
                mode,
                plan.distanceMeters(),
                plan.durationMinutes(),
                plan.polyline(),
                plan.steps(),
                plan.summary()
        );
    }

    private RouteSegmentDTO fallbackSegment(
            int order,
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode
    ) {
        int distanceMeters = origin.distanceToNextMeters() == null ? 0 : origin.distanceToNextMeters();
        int durationMinutes = origin.durationToNextMinutes() == null ? 1 : origin.durationToNextMinutes();
        String summary = this.modeLabel(mode) + "约 " + Math.max(1, durationMinutes) + " 分钟";
        List<GeoPointDTO> polyline = List.of(origin.location(), destination.location());
        return new RouteSegmentDTO(
                order,
                origin.stopId(),
                destination.stopId(),
                mode,
                distanceMeters,
                Math.max(1, durationMinutes),
                polyline,
                List.of(new RouteStepDTO(1, summary, "", distanceMeters, Math.max(1, durationMinutes), polyline)),
                summary
        );
    }

    private RouteStopDTO withSegmentCost(RouteStopDTO stop, RouteSegmentDTO segment) {
        return new RouteStopDTO(
                stop.stopId(),
                stop.order(),
                stop.name(),
                stop.slotLabel(),
                stop.category(),
                stop.location(),
                stop.rating(),
                stop.stayMinutes(),
                segment.mode(),
                segment.distanceMeters(),
                segment.durationMinutes(),
                stop.description(),
                stop.imageUrls(),
                stop.reason(),
                stop.riskNote()
        );
    }

    private String modeLabel(SegmentTransportMode mode) {
        return switch (mode) {
            case WALK -> "步行";
            case BIKE -> "骑行";
            case TAXI, DRIVE -> "驾车";
            case BUS, SUBWAY, TRANSIT -> "公共交通";
        };
    }
}
