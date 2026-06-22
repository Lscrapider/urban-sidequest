package com.urbansidequest.backend.handler.route.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BeamSearchRouteSelectorTest {

    @Test
    void fallbackRouteDoesNotAppendStopsPastAvailableDuration() {
        RouteGenerateParam param = baseParam(120);
        RouteGenerationContext context = new RouteGenerationContext(UUID.randomUUID(), UUID.randomUUID(), param);
        context.setPoiCandidates(List.of(
                poi("p1", "地点 1", true),
                poi("p2", "地点 2", true),
                poi("p3", "地点 3", false),
                poi("p4", "地点 4", false),
                poi("p5", "地点 5", false)
        ));

        BeamSearchRouteSelector selector = new BeamSearchRouteSelector(List.of());

        List<CandidateRouteDTO> routes = selector.selectRoutes(context);

        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).totalDurationMinutes()).isLessThanOrEqualTo(120);
        assertThat(context.getWarnings()).anyMatch(warning -> warning.contains("兜底路线"));
        assertThat(context.getWarnings()).anyMatch(warning -> warning.contains("部分兜底路线"));
    }

    private static RouteGenerateParam baseParam(int durationMinutes) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setDepartureTime(LocalDateTime.of(2026, 6, 17, 10, 0));
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.STEADY);
        param.setInterestTags(List.of("city"));
        return param;
    }

    private static PoiCandidateDTO poi(String poiId, String name, boolean mustVisit) {
        return new PoiCandidateDTO(
                poiId,
                "amap-" + poiId,
                name,
                "SCENIC",
                mustVisit ? PoiCandidateRole.MUST_VISIT : PoiCandidateRole.ANCHOR,
                new GeoPointDTO(new BigDecimal("116.397"), new BigDecimal("39.908")),
                "地址",
                "说明",
                new BigDecimal("4.5"),
                null,
                List.of("city"),
                List.of(),
                List.of(),
                "UNKNOWN",
                mustVisit,
                "适合路线"
        );
    }
}
