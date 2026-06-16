package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.search.BeamSearchRouteSelector;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BuildCandidateRoutesStep implements RouteGenerationStep {

    private final BeamSearchRouteSelector beamSearchRouteSelector;

    public BuildCandidateRoutesStep(BeamSearchRouteSelector beamSearchRouteSelector) {
        this.beamSearchRouteSelector = beamSearchRouteSelector;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getPoiCandidates().isEmpty()) {
            context.setCandidateRoutes(List.of());
            context.addWarning("当前范围内没有可用候选点");
            return;
        }
        List<CandidateRouteDTO> routes = this.beamSearchRouteSelector.selectRoutes(context);
        context.setCandidateRoutes(routes);
    }
}
