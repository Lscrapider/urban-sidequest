package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.converter.route.RouteGenerationConverter;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureExtractor;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureSnapshot;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotBuilder;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceTrainingIngestPayload;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.provider.route.training.RoutePreferenceTrainingObjectStore;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SaveRoutePreferenceTrainingSamplesStep implements RouteGenerationStep {

    private final RouteInputFeatureExtractor routeInputFeatureExtractor;

    private final RoutePreferenceRawSnapshotBuilder routePreferenceRawSnapshotBuilder;

    private final RouteGenerationConverter routeGenerationConverter;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final RoutePreferenceTrainingObjectStore routePreferenceTrainingObjectStore;

    public SaveRoutePreferenceTrainingSamplesStep(
            RouteInputFeatureExtractor routeInputFeatureExtractor,
            RoutePreferenceRawSnapshotBuilder routePreferenceRawSnapshotBuilder,
            RouteGenerationConverter routeGenerationConverter,
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            RoutePreferenceTrainingObjectStore routePreferenceTrainingObjectStore
    ) {
        this.routeInputFeatureExtractor = routeInputFeatureExtractor;
        this.routePreferenceRawSnapshotBuilder = routePreferenceRawSnapshotBuilder;
        this.routeGenerationConverter = routeGenerationConverter;
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routePreferenceTrainingObjectStore = routePreferenceTrainingObjectStore;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getSelectedRoutes().isEmpty()) {
            return;
        }
        this.routeGenerationHistoryManage.upsertHistory(this.routeGenerationConverter.toRouteGenerationVO(context));
        Map<String, RouteInputFeatureSnapshot> snapshotsByRouteCode = this.routeInputFeatureExtractor.extractCandidateSet(context);
        List<RoutePreferenceTrainingIngestPayload.TrainingSample> trainingSamples = new ArrayList<>();
        for (CandidateRouteDTO route : context.getSelectedRoutes()) {
            RouteInputFeatureSnapshot snapshot = snapshotsByRouteCode.get(route.routeCode());
            trainingSamples.add(RoutePreferenceTrainingIngestPayload.TrainingSample.from(
                    context.getCandidateSetId(),
                    context.getRequestId(),
                    context.getUserId(),
                    route.routeCode(),
                    snapshot
            ));
        }
        this.routePreferenceTrainingObjectStore.writeCandidateSet(new RoutePreferenceTrainingIngestPayload(
                context.getCandidateSetId(),
                context.getRequestId(),
                context.getUserId(),
                OffsetDateTime.now(),
                this.routePreferenceRawSnapshotBuilder.build(context),
                trainingSamples
        ));
    }
}
