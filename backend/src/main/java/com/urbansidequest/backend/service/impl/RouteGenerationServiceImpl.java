package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.GeoPointVO;
import com.urbansidequest.backend.domain.vo.RouteAreaVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteStopVO;
import com.urbansidequest.backend.service.RouteGenerationService;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RouteGenerationServiceImpl implements RouteGenerationService {

    private final List<RouteGenerationStep> routeGenerationSteps;

    public RouteGenerationServiceImpl(List<RouteGenerationStep> routeGenerationSteps) {
        this.routeGenerationSteps = routeGenerationSteps;
    }

    @Override
    public RouteGenerationVO generate(AuthenticatedUser authenticatedUser, RouteGenerateParam generateParam) {
        RouteGenerationContext context = new RouteGenerationContext(UUID.randomUUID(), authenticatedUser.id(), generateParam);
        for (RouteGenerationStep routeGenerationStep : this.routeGenerationSteps) {
            routeGenerationStep.execute(context);
        }
        return new RouteGenerationVO(
                context.getRequestId(),
                RouteRequestStatus.SUCCESS,
                this.toAreaVO(context.getArea(), generateParam.getDurationMinutes()),
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
                route.stops().stream().map(this::toRouteStopVO).toList()
        );
    }

    private RouteStopVO toRouteStopVO(RouteStopDTO stop) {
        return new RouteStopVO(
                stop.stopId(),
                stop.order(),
                stop.name(),
                stop.category(),
                this.toGeoPointVO(stop.location()),
                stop.stayMinutes(),
                stop.transportToNext(),
                stop.distanceToNextMeters(),
                stop.durationToNextMinutes(),
                stop.reason(),
                stop.riskNote()
        );
    }

    private GeoPointVO toGeoPointVO(GeoPointDTO point) {
        return new GeoPointVO(point.longitudeGcj02(), point.latitudeGcj02());
    }
}
