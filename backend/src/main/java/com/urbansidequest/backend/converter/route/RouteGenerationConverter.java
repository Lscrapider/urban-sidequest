package com.urbansidequest.backend.converter.route;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteSegmentDTO;
import com.urbansidequest.backend.domain.dto.RouteStepDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.GeoPointVO;
import com.urbansidequest.backend.domain.vo.RouteAreaVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteSegmentVO;
import com.urbansidequest.backend.domain.vo.RouteStepVO;
import com.urbansidequest.backend.domain.vo.RouteStopVO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class RouteGenerationConverter {

    public RouteGenerationVO toRouteGenerationVO(RouteGenerationContext context) {
        return new RouteGenerationVO(
                context.getRequestId(),
                context.getCandidateSetId(),
                RouteRequestStatus.SUCCESS,
                this.toAreaVO(context.getArea(), context.getGenerateParam().getDurationMinutes()),
                context.getSelectedRoutes().stream().map(this::toGeneratedRouteVO).toList(),
                context.getWarnings()
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

    private GeneratedRouteVO toGeneratedRouteVO(CandidateRouteDTO route) {
        return new GeneratedRouteVO(
                route.routeCode(),
                route.title(),
                route.summary(),
                route.totalDurationMinutes(),
                route.totalDistanceMeters(),
                route.budgetCent(),
                route.riskLevel(),
                route.explanation(),
                route.stops().stream().map(this::toRouteStopVO).toList(),
                route.segments().stream().map(this::toRouteSegmentVO).toList()
        );
    }

    private RouteStopVO toRouteStopVO(RouteStopDTO stop) {
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
                stop.riskNote()
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
                segment.summary()
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
}
