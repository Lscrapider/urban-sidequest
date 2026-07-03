package com.urbansidequest.backend.converter.route;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteSegmentDTO;
import com.urbansidequest.backend.domain.dto.RouteStepDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.GeoPointVO;
import com.urbansidequest.backend.domain.vo.RouteAreaVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteSegmentVO;
import com.urbansidequest.backend.domain.vo.RouteStepVO;
import com.urbansidequest.backend.domain.vo.RouteStopVO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticProfile;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import com.urbansidequest.backend.handler.route.support.RouteStopIdSupport;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RouteGenerationConverter {

    private final PoiSemanticResolver poiSemanticResolver;

    public RouteGenerationConverter(PoiSemanticResolver poiSemanticResolver) {
        this.poiSemanticResolver = poiSemanticResolver;
    }

    public RouteGenerationVO toRouteGenerationVO(RouteGenerationContext context) {
        return this.toRouteGenerationVO(context, RouteRequestStatus.SUCCESS, "completed");
    }

    public RouteGenerationVO toRouteGenerationVO(
            RouteGenerationContext context,
            RouteRequestStatus status,
            String generationStage
    ) {
        Map<String, PoiCandidateDTO> candidatesByPoiId = this.candidatesByPoiId(context);
        return new RouteGenerationVO(
                context.getRequestId(),
                context.getCandidateSetId(),
                context.getUserId(),
                status,
                this.toAreaVO(context),
                context.getSelectedRoutes().stream()
                        .map(route -> this.toGeneratedRouteVO(route, context, candidatesByPoiId))
                        .toList(),
                context.getWarnings(),
                generationStage,
                null,
                RouteExecutionStatus.GENERATED
        );
    }

    private RouteAreaVO toAreaVO(RouteGenerationContext context) {
        RouteAreaDTO area = context.getArea();
        if (area != null) {
            return this.toAreaVO(area, context.getGenerateParam().getDurationMinutes());
        }
        GeoPointParam center = context.getGenerateParam().getCenter();
        return new RouteAreaVO(
                context.getGenerateParam().getAreaMode(),
                context.getGenerateParam().getAreaLabel() == null ? "路线生成中" : context.getGenerateParam().getAreaLabel(),
                center == null ? new GeoPointVO(BigDecimal.ZERO, BigDecimal.ZERO) : this.toGeoPointVO(center),
                context.getGenerateParam().getRadiusMeters() == null ? 0 : context.getGenerateParam().getRadiusMeters(),
                List.of(),
                "正在生成路线"
        );
    }

    private RouteAreaVO toAreaVO(RouteAreaDTO area, int durationMinutes) {
        return new RouteAreaVO(
                area.areaMode(),
                area.areaLabel(),
                this.toGeoPointVO(area.center()),
                area.radiusMeters(),
                area.polygonGcj02().stream().map(this::toGeoPointVO).toList(),
                "当前范围适合 " + durationMinutes + " 分钟路线"
        );
    }

    private GeneratedRouteVO toGeneratedRouteVO(
            CandidateRouteDTO route,
            RouteGenerationContext context,
            Map<String, PoiCandidateDTO> candidatesByPoiId
    ) {
        return new GeneratedRouteVO(
                route.routeCode(),
                route.title(),
                route.summary(),
                route.totalDurationMinutes(),
                route.totalDistanceMeters(),
                route.budgetCent(),
                route.riskLevel(),
                route.explanation(),
                route.stops().stream()
                        .map(stop -> this.toRouteStopVO(stop, route.routeCode(), context, candidatesByPoiId))
                        .toList(),
                route.segments().stream().map(this::toRouteSegmentVO).toList()
        );
    }

    private RouteStopVO toRouteStopVO(
            RouteStopDTO stop,
            String routeCode,
            RouteGenerationContext context,
            Map<String, PoiCandidateDTO> candidatesByPoiId
    ) {
        String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), routeCode);
        PoiCandidateDTO candidate = candidatesByPoiId.get(poiId);
        PoiSemanticProfile semantic = candidate == null
                ? PoiSemanticProfile.empty()
                : this.poiSemanticResolver.resolve(candidate, context.getPoiSemanticMappings());
        return new RouteStopVO(
                stop.stopId(),
                stop.order(),
                stop.name(),
                stop.slotLabel(),
                stop.category(),
                stop.routeRole(),
                stop.intendedMealWindow(),
                this.toGeoPointVO(stop.location()),
                stop.rating(),
                stop.stayMinutes(),
                stop.transportToNext(),
                stop.distanceToNextMeters(),
                stop.durationToNextMinutes(),
                stop.description(),
                stop.imageUrls(),
                stop.reason(),
                stop.riskNote(),
                semantic.primaryCategoryGroup(),
                semantic.categoryGroups().stream().toList(),
                semantic.semanticTags(),
                semantic.poiTagHits().stream().toList(),
                semantic.isMealCandidate(),
                semantic.isRestCandidate(),
                semantic.localExperienceCandidate(),
                candidate == null ? null : candidate.rawType(),
                candidate == null ? null : candidate.typecode(),
                candidate == null ? null : candidate.avgPriceCent()
        );
    }

    private RouteSegmentVO toRouteSegmentVO(RouteSegmentDTO segment) {
        return new RouteSegmentVO(
                segment.order(),
                segment.originStopId(),
                segment.destinationStopId(),
                segment.mode(),
                segment.distanceMeters(),
                segment.durationMinutes(),
                segment.polyline().stream().map(this::toGeoPointVO).toList(),
                segment.steps().stream().map(this::toRouteStepVO).toList(),
                segment.summary(),
                segment.source()
        );
    }

    private RouteStepVO toRouteStepVO(RouteStepDTO step) {
        return new RouteStepVO(
                step.order(),
                step.instruction(),
                step.roadName(),
                step.distanceMeters(),
                step.durationMinutes(),
                step.polyline().stream().map(this::toGeoPointVO).toList()
        );
    }

    private GeoPointVO toGeoPointVO(GeoPointDTO point) {
        return new GeoPointVO(point.longitudeGcj02(), point.latitudeGcj02());
    }

    private GeoPointVO toGeoPointVO(GeoPointParam point) {
        return new GeoPointVO(point.getLongitudeGcj02(), point.getLatitudeGcj02());
    }

    private Map<String, PoiCandidateDTO> candidatesByPoiId(RouteGenerationContext context) {
        Map<String, PoiCandidateDTO> candidatesByPoiId = new LinkedHashMap<>();
        for (PoiCandidateDTO candidate : context.getPoiCandidates()) {
            candidatesByPoiId.put(candidate.poiId(), candidate);
        }
        return candidatesByPoiId;
    }
}
