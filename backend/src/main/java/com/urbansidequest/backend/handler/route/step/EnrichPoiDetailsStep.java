package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.provider.route.PoiDetailProvider;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

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
