package com.urbansidequest.backend.handler.route.config;

import java.nio.file.Path;

public final class RouteScoringTestSupport {

    private RouteScoringTestSupport() {
    }

    public static RouteScoringProperties properties() {
        return new RouteScoringProperties(Path.of("src/test/resources/route-scoring-test.yml"));
    }
}
