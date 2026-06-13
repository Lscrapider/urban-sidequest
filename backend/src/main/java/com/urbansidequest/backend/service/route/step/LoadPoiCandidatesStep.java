package com.urbansidequest.backend.service.route.step;

import com.urbansidequest.backend.provider.route.PoiCandidateProvider;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(40)
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
