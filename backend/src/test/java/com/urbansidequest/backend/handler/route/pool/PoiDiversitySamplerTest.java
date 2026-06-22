package com.urbansidequest.backend.handler.route.pool;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PoiDiversitySamplerTest {

    @Test
    void reservesCleanMidFarPoisForTaxiBeforeOrdinarySampler() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(TransportProfile.WALK_TAXI, 360)
        );
        PoiDiversitySampler sampler = new PoiDiversitySampler();
        List<PoiDiversitySampler.RankedPoi> rankedCandidates = List.of(
                ranked("near-1", 1000, 0.40, 10.0),
                ranked("near-2", 1200, 0.40, 9.0),
                ranked("near-3", 1400, 0.40, 8.0),
                ranked("near-4", 1600, 0.40, 7.0),
                ranked("mid-clean", 5000, 0.80, -10.0),
                ranked("far-clean", 9000, 0.75, -10.0),
                ranked("far-low-quality", 9000, 0.05, -10.0)
        );

        List<String> selectedPoiIds = sampler.sample(context, rankedCandidates, 4).stream()
                .map(item -> item.candidate().poiId())
                .toList();

        assertThat(selectedPoiIds)
                .contains("mid-clean", "far-clean")
                .doesNotContain("far-low-quality");
    }

    private static PoiDiversitySampler.RankedPoi ranked(
            String poiId,
            double distanceMeters,
            double cleanQuality,
            double linearScore
    ) {
        return new PoiDiversitySampler.RankedPoi(
                poi(poiId),
                score(poiId, distanceMeters, cleanQuality, linearScore)
        );
    }

    private static PoiCandidateDTO poi(String poiId) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                poiId,
                "LOCAL",
                PoiCandidateRole.LOCAL,
                new GeoPointDTO(new BigDecimal("121.4737"), new BigDecimal("31.2304")),
                "address",
                "description",
                new BigDecimal("4.6"),
                null,
                List.of("LOCAL"),
                List.of(),
                List.of(),
                "MEDIUM",
                false,
                "reason"
        );
    }

    private static PoiLinearScoreDTO score(
            String poiId,
            double distanceMeters,
            double cleanQuality,
            double linearScore
    ) {
        return new PoiLinearScoreDTO(
                poiId,
                poiId,
                cleanQuality,
                0d,
                0d,
                0d,
                -0.5d,
                0d,
                0d,
                0d,
                distanceMeters,
                9500d,
                distanceMeters / 9500d,
                0d,
                0d,
                0d,
                linearScore
        );
    }

    private static RouteGenerateParam baseParam(TransportProfile transportProfile, int durationMinutes) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setCenter(center());
        param.setDepartureTime(LocalDateTime.of(2026, 6, 17, 10, 0));
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(transportProfile);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private static GeoPointParam center() {
        GeoPointParam center = new GeoPointParam();
        center.setLongitudeGcj02(new BigDecimal("121.4737"));
        center.setLatitudeGcj02(new BigDecimal("31.2304"));
        return center;
    }
}
