package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.config.RoutePreferenceTrainingProperties;
import com.urbansidequest.backend.converter.route.RouteGenerationConverter;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureExtractor;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureSnapshot;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotBuilder;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.manage.RoutePreferenceRawSnapshotManage;
import com.urbansidequest.backend.manage.RoutePreferenceTrainingSampleManage;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SaveRoutePreferenceTrainingSamplesStep implements RouteGenerationStep {

    private final RouteInputFeatureExtractor routeInputFeatureExtractor;

    private final RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage;

    private final RoutePreferenceRawSnapshotBuilder routePreferenceRawSnapshotBuilder;

    private final RoutePreferenceRawSnapshotManage routePreferenceRawSnapshotManage;

    private final RouteGenerationConverter routeGenerationConverter;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final RoutePreferenceTrainingProperties routePreferenceTrainingProperties;

    public SaveRoutePreferenceTrainingSamplesStep(
            RouteInputFeatureExtractor routeInputFeatureExtractor,
            RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage,
            RoutePreferenceRawSnapshotBuilder routePreferenceRawSnapshotBuilder,
            RoutePreferenceRawSnapshotManage routePreferenceRawSnapshotManage,
            RouteGenerationConverter routeGenerationConverter,
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            RoutePreferenceTrainingProperties routePreferenceTrainingProperties
    ) {
        this.routeInputFeatureExtractor = routeInputFeatureExtractor;
        this.routePreferenceTrainingSampleManage = routePreferenceTrainingSampleManage;
        this.routePreferenceRawSnapshotBuilder = routePreferenceRawSnapshotBuilder;
        this.routePreferenceRawSnapshotManage = routePreferenceRawSnapshotManage;
        this.routeGenerationConverter = routeGenerationConverter;
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routePreferenceTrainingProperties = routePreferenceTrainingProperties;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getSelectedRoutes().isEmpty()) {
            return;
        }
        if (this.routePreferenceTrainingProperties.isRawSnapshotEnabled()) {
            this.routePreferenceRawSnapshotManage.upsertSnapshot(this.routePreferenceRawSnapshotBuilder.build(context));
        }
        this.routeGenerationHistoryManage.upsertHistory(this.routeGenerationConverter.toRouteGenerationVO(context));
        Map<String, RouteInputFeatureSnapshot> snapshotsByRouteCode = this.routeInputFeatureExtractor.extractCandidateSet(context);
        for (CandidateRouteDTO route : context.getSelectedRoutes()) {
            RouteInputFeatureSnapshot snapshot = snapshotsByRouteCode.get(route.routeCode());
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
