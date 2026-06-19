package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureExtractor;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureSnapshot;
import com.urbansidequest.backend.manage.RoutePreferenceTrainingSampleManage;
import org.springframework.stereotype.Component;

@Component
public class SaveRoutePreferenceTrainingSamplesStep implements RouteGenerationStep {

    private final RouteInputFeatureExtractor routeInputFeatureExtractor;

    private final RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage;

    public SaveRoutePreferenceTrainingSamplesStep(
            RouteInputFeatureExtractor routeInputFeatureExtractor,
            RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage
    ) {
        this.routeInputFeatureExtractor = routeInputFeatureExtractor;
        this.routePreferenceTrainingSampleManage = routePreferenceTrainingSampleManage;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getCandidateRoutes().isEmpty()) {
            return;
        }
        for (CandidateRouteDTO route : context.getCandidateRoutes()) {
            RouteInputFeatureSnapshot snapshot = this.routeInputFeatureExtractor.extract(route, context);
            this.routePreferenceTrainingSampleManage.upsertGeneratedSample(
                    context.getCandidateSetId(),
                    context.getRequestId(),
                    context.getUserId(),
                    route.routeCode(),
                    snapshot
            );
        }
    }
}
