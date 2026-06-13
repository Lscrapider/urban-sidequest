package com.urbansidequest.backend.service.route.step;

import com.urbansidequest.backend.provider.route.PoiDetailProvider;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(50)
@Component
public class EnrichPoiDetailsStep implements RouteGenerationStep {

    private final PoiDetailProvider poiDetailProvider;

    public EnrichPoiDetailsStep(PoiDetailProvider poiDetailProvider) {
        this.poiDetailProvider = poiDetailProvider;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setPoiCandidates(this.poiDetailProvider.enrichDetails(context, context.getPoiCandidates()));
    }
}
