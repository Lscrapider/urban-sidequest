package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.provider.route.PoiCandidateProvider;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class LoadPoiCandidatesStep implements RouteGenerationStep {

    private final PoiCandidateProvider poiCandidateProvider;

    public LoadPoiCandidatesStep(PoiCandidateProvider poiCandidateProvider) {
        this.poiCandidateProvider = poiCandidateProvider;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setPoiCandidates(this.poiCandidateProvider.loadCandidates(context));
    }
}
