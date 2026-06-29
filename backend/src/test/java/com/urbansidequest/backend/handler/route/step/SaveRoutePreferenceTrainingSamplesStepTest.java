package com.urbansidequest.backend.handler.route.step;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.urbansidequest.backend.config.RoutePreferenceTrainingProperties;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureExtractor;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureSnapshot;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotBuilder;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotPayload;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotSchema;
import com.urbansidequest.backend.manage.RoutePreferenceRawSnapshotManage;
import com.urbansidequest.backend.manage.RoutePreferenceTrainingSampleManage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaveRoutePreferenceTrainingSamplesStepTest {

    @Test
    void writesRawSnapshotWhenConfigEnabled() {
        Fixture fixture = fixture(true);

        fixture.step().execute(fixture.context());

        verify(fixture.rawSnapshotBuilder()).build(fixture.context());
        verify(fixture.rawSnapshotManage()).upsertSnapshot(fixture.payload());
        verify(fixture.trainingSampleManage()).upsertGeneratedSample(
                fixture.context().getCandidateSetId(),
                fixture.context().getRequestId(),
                fixture.context().getUserId(),
                "A",
                fixture.snapshot()
        );
    }

    @Test
    void skipsRawSnapshotWhenConfigDisabledButStillWritesSample() {
        Fixture fixture = fixture(false);

        fixture.step().execute(fixture.context());

        verify(fixture.rawSnapshotBuilder(), never()).build(fixture.context());
        verify(fixture.rawSnapshotManage(), never()).upsertSnapshot(fixture.payload());
        verify(fixture.trainingSampleManage()).upsertGeneratedSample(
                fixture.context().getCandidateSetId(),
                fixture.context().getRequestId(),
                fixture.context().getUserId(),
                "A",
                fixture.snapshot()
        );
    }

    private static Fixture fixture(boolean rawSnapshotEnabled) {
        RouteInputFeatureExtractor extractor = mock(RouteInputFeatureExtractor.class);
        RoutePreferenceTrainingSampleManage trainingSampleManage = mock(RoutePreferenceTrainingSampleManage.class);
        RoutePreferenceRawSnapshotBuilder rawSnapshotBuilder = mock(RoutePreferenceRawSnapshotBuilder.class);
        RoutePreferenceRawSnapshotManage rawSnapshotManage = mock(RoutePreferenceRawSnapshotManage.class);
        RoutePreferenceTrainingProperties properties = new RoutePreferenceTrainingProperties();
        properties.setRawSnapshotEnabled(rawSnapshotEnabled);
        SaveRoutePreferenceTrainingSamplesStep step = new SaveRoutePreferenceTrainingSamplesStep(
                extractor,
                trainingSampleManage,
                rawSnapshotBuilder,
                rawSnapshotManage,
                properties
        );
        RouteGenerationContext context = context();
        RouteInputFeatureSnapshot snapshot = new RouteInputFeatureSnapshot("v-test", "[]", "[]", "{}", "{}", "{}", "{}");
        RoutePreferenceRawSnapshotPayload payload = payload(context);
        when(extractor.extractCandidateSet(context)).thenReturn(Map.of("A", snapshot));
        when(rawSnapshotBuilder.build(context)).thenReturn(payload);
        return new Fixture(
                step,
                context,
                rawSnapshotBuilder,
                rawSnapshotManage,
                trainingSampleManage,
                payload,
                snapshot
        );
    }

    private static RouteGenerationContext context() {
        RouteGenerationContext context = new RouteGenerationContext(UUID.randomUUID(), UUID.randomUUID(), baseParam());
        context.setSelectedRoutes(List.of(new CandidateRouteDTO(
                "A",
                "路线 A",
                "summary",
                120,
                0,
                null,
                RiskLevel.LOW,
                "explanation",
                List.of(),
                List.of(),
                0
        )));
        return context;
    }

    private static RoutePreferenceRawSnapshotPayload payload(RouteGenerationContext context) {
        return new RoutePreferenceRawSnapshotPayload(
                context.getCandidateSetId(),
                context.getRequestId(),
                context.getUserId(),
                RoutePreferenceRawSnapshotSchema.VERSION,
                context.getGenerateParam(),
                context.getArea(),
                RouteWeatherDTO.unavailable(),
                UserPreferenceProfileDTO.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                context.getSelectedRoutes(),
                List.of(),
                List.of()
        );
    }

    private static RouteGenerateParam baseParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setDepartureTime(LocalDateTime.of(2026, 6, 23, 10, 0));
        param.setDurationMinutes(120);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private record Fixture(
            SaveRoutePreferenceTrainingSamplesStep step,
            RouteGenerationContext context,
            RoutePreferenceRawSnapshotBuilder rawSnapshotBuilder,
            RoutePreferenceRawSnapshotManage rawSnapshotManage,
            RoutePreferenceTrainingSampleManage trainingSampleManage,
            RoutePreferenceRawSnapshotPayload payload,
            RouteInputFeatureSnapshot snapshot
    ) {
    }
}
