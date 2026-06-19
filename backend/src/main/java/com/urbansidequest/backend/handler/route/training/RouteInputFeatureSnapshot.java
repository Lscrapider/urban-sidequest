package com.urbansidequest.backend.handler.route.training;

public record RouteInputFeatureSnapshot(
        String featureSchemaVersion,
        String stopMatrixJson,
        String segmentMatrixJson,
        String routeDerivedVectorJson,
        String contextCrossVectorJson,
        String contextJson
) {
}
