package com.urbansidequest.backend.handler.route.training;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoutePreferenceTrainingIngestPayload(
        UUID candidateSetId,
        UUID requestId,
        UUID userId,
        OffsetDateTime createdAt,
        RoutePreferenceRawSnapshotPayload rawSnapshot,
        List<TrainingSample> trainingSamples
) {

    public RoutePreferenceTrainingIngestPayload {
        trainingSamples = trainingSamples == null ? List.of() : List.copyOf(trainingSamples);
    }

    public record TrainingSample(
            UUID candidateSetId,
            UUID requestId,
            UUID userId,
            String routeCode,
            String featureSchemaVersion,
            String stopMatrixJson,
            String segmentMatrixJson,
            String routeDerivedVectorJson,
            String contextCrossVectorJson,
            String intraSetVectorJson,
            String contextJson
    ) {

        public static TrainingSample from(
                UUID candidateSetId,
                UUID requestId,
                UUID userId,
                String routeCode,
                RouteInputFeatureSnapshot snapshot
        ) {
            return new TrainingSample(
                    candidateSetId,
                    requestId,
                    userId,
                    routeCode,
                    snapshot.featureSchemaVersion(),
                    snapshot.stopMatrixJson(),
                    snapshot.segmentMatrixJson(),
                    snapshot.routeDerivedVectorJson(),
                    snapshot.contextCrossVectorJson(),
                    snapshot.intraSetVectorJson(),
                    snapshot.contextJson()
            );
        }
    }
}
