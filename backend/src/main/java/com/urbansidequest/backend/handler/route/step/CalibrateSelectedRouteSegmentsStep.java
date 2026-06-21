package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.api.amap.AmapApi;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RoutePlanDTO;
import com.urbansidequest.backend.domain.dto.RoutePlanResultDTO;
import com.urbansidequest.backend.domain.dto.RouteSegmentDTO;
import com.urbansidequest.backend.domain.dto.RouteStepDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.RoutePlanStatus;
import com.urbansidequest.backend.domain.enums.RouteSegmentSource;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.handler.route.SegmentModeResolver;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.LinearScoreConstants;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import com.urbansidequest.backend.manage.RouteSegmentCostCacheManage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 对最终选中的路线逐段补真实路径规划结果。
 *
 * <p>前置步骤只生成 stop 顺序和推荐交通方式；本步骤优先读取路段缓存，
 * 未命中时调用高德路线规划，补齐真实距离、耗时、polyline 和 steps。</p>
 */
@Component
public class CalibrateSelectedRouteSegmentsStep implements RouteGenerationStep {

    private final AmapApi amapApi;

    private final RouteSegmentCostCacheManage routeSegmentCostCacheManage;

    private final SegmentModeResolver segmentModeResolver;

    public CalibrateSelectedRouteSegmentsStep(
            AmapApi amapApi,
            RouteSegmentCostCacheManage routeSegmentCostCacheManage,
            SegmentModeResolver segmentModeResolver
    ) {
        this.amapApi = amapApi;
        this.routeSegmentCostCacheManage = routeSegmentCostCacheManage;
        this.segmentModeResolver = segmentModeResolver;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getSelectedRoutes().isEmpty()) {
            return;
        }
        Map<String, PoiCandidateDTO> candidatesByPoiId = this.candidatesByPoiId(context);
        List<CandidateRouteDTO> calibratedRoutes = context.getSelectedRoutes().stream()
                .map(route -> this.calibrateRoute(route, context, candidatesByPoiId))
                .flatMap(Optional::stream)
                .toList();
        if (calibratedRoutes.isEmpty()) {
            context.addWarning("所有候选路线在真实路径校准后均不可用，未保存路线偏好训练样本");
        }
        context.setSelectedRoutes(calibratedRoutes);
    }

    private Optional<CandidateRouteDTO> calibrateRoute(
            CandidateRouteDTO route,
            RouteGenerationContext context,
            Map<String, PoiCandidateDTO> candidatesByPoiId
    ) {
        List<RouteStopDTO> stops = route.stops();
        if (stops.size() < 2) {
            return Optional.of(route);
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
            SegmentTransportMode initialMode = this.segmentModeResolver.resolveInitialMode(
                    context.getGenerateParam().getTransportProfile(),
                    current,
                    next,
                    candidatesByPoiId,
                    route.routeCode()
            );
            Optional<RouteSegmentDTO> segmentOptional = this.resolveSegment(
                    route.routeCode(),
                    index + 1,
                    current,
                    next,
                    initialMode,
                    context
            );
            if (segmentOptional.isEmpty()) {
                context.addWarning(route.routeCode() + " 线第 " + (index + 1) + " 段路径规划失败且超过本地步行兜底上限，已丢弃该候选路线");
                return Optional.empty();
            }
            RouteSegmentDTO segment = segmentOptional.get();
            segments.add(segment);
            calibratedStops.add(this.withSegmentCost(current, segment));
        }

        int calibratedDurationMinutes = calibratedStops.stream().mapToInt(RouteStopDTO::stayMinutes).sum()
                + segments.stream().mapToInt(RouteSegmentDTO::durationMinutes).sum();
        int calibratedDistanceMeters = segments.stream().mapToInt(RouteSegmentDTO::distanceMeters).sum();
        if (calibratedDurationMinutes > context.getGenerateParam().getDurationMinutes()) {
            context.addWarning(route.routeCode() + " 线真实路径校准后超过预设时长，请执行前留意时间余量");
        }
        return Optional.of(new CandidateRouteDTO(
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
        ));
    }

    private Optional<RouteSegmentDTO> resolveSegment(
            String routeCode,
            int order,
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode initialMode,
            RouteGenerationContext context
    ) {
        List<SegmentTransportMode> modesToTry = this.modesToTry(initialMode);
        for (int index = 0; index < modesToTry.size(); index++) {
            SegmentTransportMode mode = modesToTry.get(index);
            if (this.hasNoRouteCache(origin, destination, mode)) {
                continue;
            }
            Optional<RoutePlanDTO> plan = this.findCachedPlan(origin, destination, mode)
                    .or(() -> this.fetchAndCachePlan(origin, destination, mode, context));
            if (plan.isPresent()) {
                RouteSegmentSource source = index == 0 ? RouteSegmentSource.AMAP_DIRECT : RouteSegmentSource.AMAP_FALLBACK;
                if (source == RouteSegmentSource.AMAP_FALLBACK) {
                    context.addWarning(routeCode + " 线第 " + order + " 段 " + this.modeLabel(initialMode)
                            + " 无可用路线，已降级为 " + this.modeLabel(mode));
                }
                return Optional.of(this.toRouteSegment(order, origin, destination, mode, plan.get(), source));
            }
        }
        Optional<RouteSegmentDTO> fallbackSegment = this.fallbackSegment(order, origin, destination);
        if (fallbackSegment.isPresent()) {
            context.addWarning(routeCode + " 线第 " + order + " 段路径规划失败，已使用步行直线估算");
        }
        return fallbackSegment;
    }

    private List<SegmentTransportMode> modesToTry(SegmentTransportMode initialMode) {
        Set<SegmentTransportMode> modes = new LinkedHashSet<>();
        modes.add(initialMode);
        modes.addAll(this.segmentModeResolver.fallbackChain(initialMode));
        return new ArrayList<>(modes);
    }

    private Optional<RoutePlanDTO> findCachedPlan(
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode
    ) {
        if (origin.location() == null || destination.location() == null) {
            return Optional.empty();
        }
        return this.routeSegmentCostCacheManage.findLatestRawPayload(origin.location(), destination.location(), mode)
                .flatMap(rawPayload -> this.amapApi.parseCachedPlan(rawPayload, mode));
    }

    private boolean hasNoRouteCache(
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode
    ) {
        if (origin.location() == null || destination.location() == null) {
            return false;
        }
        return this.routeSegmentCostCacheManage.isLatestNoRoute(origin.location(), destination.location(), mode);
    }

    private Optional<RoutePlanDTO> fetchAndCachePlan(
            RouteStopDTO origin,
            RouteStopDTO destination,
            SegmentTransportMode mode,
            RouteGenerationContext context
    ) {
        if (origin.location() == null || destination.location() == null) {
            return Optional.empty();
        }
        RoutePlanResultDTO result = this.amapApi.planRouteResult(
                origin.location(),
                destination.location(),
                mode,
                context.getGenerateParam().getRouteCityName(),
                context.getGenerateParam().getRouteCityAdcode()
        );
        if (result.status() == RoutePlanStatus.NO_ROUTE) {
            this.routeSegmentCostCacheManage.saveNoRoute(origin.location(), destination.location(), mode);
            return Optional.empty();
        }
        Optional<RoutePlanDTO> plan = result.planOptional();
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
            RoutePlanDTO plan,
            RouteSegmentSource source
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
                plan.summary(),
                source
        );
    }

    private Optional<RouteSegmentDTO> fallbackSegment(
            int order,
            RouteStopDTO origin,
            RouteStopDTO destination
    ) {
        if (origin.location() == null || destination.location() == null) {
            return Optional.empty();
        }
        int distanceMeters = GeoMath.distanceMeters(origin.location(), destination.location());
        if (distanceMeters > LinearScoreConstants.MAX_WALK_FALLBACK_METERS) {
            return Optional.empty();
        }
        int durationMinutes = this.estimateDurationMinutes(distanceMeters, SegmentTransportMode.WALK);
        String summary = this.modeLabel(SegmentTransportMode.WALK) + "约 " + Math.max(1, durationMinutes) + " 分钟";
        List<GeoPointDTO> polyline = List.of(origin.location(), destination.location());
        return Optional.of(new RouteSegmentDTO(
                order,
                origin.stopId(),
                destination.stopId(),
                SegmentTransportMode.WALK,
                distanceMeters,
                Math.max(1, durationMinutes),
                polyline,
                List.of(new RouteStepDTO(1, summary, "", distanceMeters, Math.max(1, durationMinutes), polyline)),
                summary,
                RouteSegmentSource.FALLBACK_STRAIGHT_LINE
        ));
    }

    private int estimateDurationMinutes(int distanceMeters, SegmentTransportMode mode) {
        return switch (mode) {
            case BIKE -> (int) Math.ceil(distanceMeters / 180d) + 3;
            case BUS, SUBWAY, TRANSIT -> (int) Math.ceil(distanceMeters / 260d) + 12;
            case TAXI, DRIVE -> (int) Math.ceil(distanceMeters / 300d) + 8;
            case WALK -> (int) Math.ceil(distanceMeters / 70d);
        };
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

    private Map<String, PoiCandidateDTO> candidatesByPoiId(RouteGenerationContext context) {
        Map<String, PoiCandidateDTO> candidatesByPoiId = new LinkedHashMap<>();
        for (PoiCandidateDTO candidate : context.getPoiCandidates()) {
            candidatesByPoiId.put(candidate.poiId(), candidate);
        }
        return candidatesByPoiId;
    }
}
