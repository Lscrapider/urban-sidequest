package com.urbansidequest.backend.handler.route.training;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class RoutePreferenceRawSnapshotBuilder {

    public RoutePreferenceRawSnapshotPayload build(RouteGenerationContext context) {
        return new RoutePreferenceRawSnapshotPayload(
                context.getCandidateSetId(),
                context.getRequestId(),
                context.getUserId(),
                RoutePreferenceRawSnapshotSchema.VERSION,
                context.getGenerateParam(),
                context.getArea(),
                context.getRouteWeather(),
                context.getUserPreferenceProfile(),
                context.getInterestTagCatalog(),
                context.getInterestTags(),
                context.getPoiSemanticMappings(),
                context.getPoiCandidates(),
                context.getPoiLinearTraces(),
                context.getSelectedRoutes(),
                context.getSegmentCosts(),
                context.getWarnings()
        );
    }
}
