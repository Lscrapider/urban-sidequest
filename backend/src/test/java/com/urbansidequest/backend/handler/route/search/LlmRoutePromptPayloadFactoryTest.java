package com.urbansidequest.backend.handler.route.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlanner;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlmRoutePromptPayloadFactoryTest {

    @Test
    void buildsDistrictPayloadInsteadOfFlatPoiPool() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam()
        );
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "测试区域",
                point("121.0000", "31.0000"),
                8000,
                List.of()
        ));
        context.setPoiCandidates(List.of(
                poi("near-1", "121.0000", "31.0000", List.of("coffee", "local")),
                poi("near-2", "121.0010", "31.0000", List.of("museum")),
                poi("far-1", "121.0500", "31.0000", List.of("photo"))
        ));
        LlmRoutePromptPayloadFactory factory = new LlmRoutePromptPayloadFactory(new RouteDistrictPlanner());

        Map<String, Object> payload = factory.toPromptPayload(context);

        assertThat(payload)
                .containsKeys("districts", "districtOrder", "districtBudget")
                .doesNotContainKey("poiPool");
        assertThat(payload.get("districtBudget")).isEqualTo(3);

        List<Map<String, Object>> districts = districts(payload);
        assertThat(districts).hasSize(2);
        assertThat(payload.get("districtOrder")).isEqualTo(districts.stream()
                .map(district -> district.get("districtId"))
                .toList());
        assertThat(districts.stream()
                .flatMap(district -> pois(district).stream())
                .map(poi -> poi.get("poiId")))
                .containsExactlyInAnyOrder("near-1", "near-2", "far-1");
        assertThat(districts.get(0))
                .containsEntry("poiCount", 2)
                .containsKey("dominantInterests");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> districts(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("districts");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pois(Map<String, Object> district) {
        return (List<Map<String, Object>>) district.get("pois");
    }

    private static RouteGenerateParam baseParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setCenter(center());
        param.setDepartureTime(Instant.parse("2026-06-17T02:00:00Z"));
        param.setDurationMinutes(360);
        param.setTransportProfile(TransportProfile.WALK_TAXI);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private static GeoPointParam center() {
        GeoPointParam center = new GeoPointParam();
        center.setLongitudeGcj02(new BigDecimal("121.0000"));
        center.setLatitudeGcj02(new BigDecimal("31.0000"));
        return center;
    }

    private static PoiCandidateDTO poi(String poiId, String longitude, String latitude, List<String> tags) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                poiId,
                "LOCAL",
                PoiCandidateRole.LOCAL,
                point(longitude, latitude),
                "address",
                "description",
                new BigDecimal("4.6"),
                null,
                tags,
                List.of(),
                List.of(),
                "MEDIUM",
                false,
                "reason"
        );
    }

    private static GeoPointDTO point(String longitude, String latitude) {
        return new GeoPointDTO(new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
