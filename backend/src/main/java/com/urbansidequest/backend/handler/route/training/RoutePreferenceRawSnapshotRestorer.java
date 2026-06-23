package com.urbansidequest.backend.handler.route.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.po.RoutePreferenceRawSnapshotPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class RoutePreferenceRawSnapshotRestorer {

    private final ObjectMapper objectMapper;

    public RoutePreferenceRawSnapshotRestorer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RouteGenerationContext restore(RoutePreferenceRawSnapshotPO rawSnapshotPO) {
        RoutePreferenceRawSnapshotPayload payload = rawSnapshotPO.toPayload(this.objectMapper);
        RouteGenerationContext context = new RouteGenerationContext(
                payload.requestId(),
                payload.candidateSetId(),
                payload.userId(),
                payload.generateParam()
        );
        context.setArea(payload.area());
        context.setRouteWeather(payload.weather());
        context.setUserPreferenceProfile(payload.userPreferenceProfile());
        context.setInterestTagCatalog(payload.interestTagCatalog());
        context.setInterestTags(payload.interestTags());
        context.setPoiSemanticMappings(payload.poiSemanticMappings());
        context.setPoiCandidates(payload.poiCandidates());
        context.setPoiLinearTraces(payload.poiLinearTraces());
        context.setSelectedRoutes(payload.selectedRoutes());
        context.setSegmentCosts(payload.segmentCosts());
        context.getWarnings().addAll(payload.warnings());
        return context;
    }
}
