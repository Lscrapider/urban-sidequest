package com.urbansidequest.backend.handler.route.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlanner;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import com.urbansidequest.backend.handler.route.pool.PoiDiversitySampler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LlmRoutePromptInputExpansionTest {

    private final PoiDiversitySampler sampler = new PoiDiversitySampler();

    private final LlmRoutePromptPayloadFactory payloadFactory =
            new LlmRoutePromptPayloadFactory(new RouteDistrictPlanner(), new PoiSemanticResolver());

    @Test
    void taxiFullDayPromptInputContainsReservedMidFarDistrictsWhenLinearCandidatesHaveCleanQuality() {
        RouteGenerationContext context = context(TransportProfile.WALK_TAXI, 420);
        List<PoiDiversitySampler.RankedPoi> rankedCandidates = new ArrayList<>(nearAnchors(10));
        rankedCandidates.add(ranked("mid-clean", "121.0800", 7000d, 0.80d, -10d, "LOCAL", PoiCandidateRole.LOCAL));
        rankedCandidates.add(ranked("far-clean", "121.1600", 14000d, 0.75d, -10d, "SCENIC", PoiCandidateRole.LOCAL));
        rankedCandidates.add(ranked("far-low-quality", "121.1800", 14500d, 0.05d, -10d, "REST", PoiCandidateRole.LOCAL));

        applySampledCandidates(context, rankedCandidates, 12);
        Map<String, Object> payload = this.payloadFactory.toPromptPayload(context);

        assertThat(payload.get("districtBudget")).isEqualTo(4);
        assertThat(districts(payload)).hasSize(3);
        assertThat(payloadPoiIds(payload))
                .contains("mid-clean", "far-clean")
                .doesNotContain("far-low-quality");
    }

    @Test
    void walkOnlyPromptInputDoesNotReserveMidFarPoisWhenNearAnchorsFillPool() {
        RouteGenerationContext context = context(TransportProfile.WALK_ONLY, 420);
        List<PoiDiversitySampler.RankedPoi> rankedCandidates = new ArrayList<>(nearAnchors(10));
        rankedCandidates.add(ranked("mid-clean", "121.0800", 7000d, 0.80d, -10d, "LOCAL", PoiCandidateRole.LOCAL));
        rankedCandidates.add(ranked("far-clean", "121.1600", 14000d, 0.75d, -10d, "SCENIC", PoiCandidateRole.LOCAL));

        applySampledCandidates(context, rankedCandidates, 10);
        Map<String, Object> payload = this.payloadFactory.toPromptPayload(context);

        assertThat(payload.get("districtBudget")).isEqualTo(1);
        assertThat(districts(payload)).hasSize(1);
        assertThat(payloadPoiIds(payload)).doesNotContain("mid-clean", "far-clean");
    }

    @Test
    void nearOnlyTaxiFullDayPromptInputStillHasOnlyOneAvailableDistrict() {
        RouteGenerationContext context = context(TransportProfile.WALK_TAXI, 420);
        applySampledCandidates(context, nearAnchors(12), 12);

        Map<String, Object> payload = this.payloadFactory.toPromptPayload(context);

        assertThat(payload.get("districtBudget")).isEqualTo(4);
        assertThat(payload.get("districtOrder")).isEqualTo(List.of("D1"));
        assertThat(districts(payload)).hasSize(1);
        assertThat(districts(payload).get(0).get("poiCount")).isEqualTo(12);
    }

    @Test
    void promptInputClustersWalkableChainTransitivelyBeforeDistrictBudget() {
        RouteGenerationContext context = context(TransportProfile.WALK_TAXI, 420);
        context.setPoiCandidates(List.of(
                poi("chain-a", "121.0000", PoiCandidateRole.LOCAL),
                poi("chain-b", "121.0080", PoiCandidateRole.LOCAL),
                poi("chain-c", "121.0160", PoiCandidateRole.LOCAL),
                poi("gap-d", "121.0400", PoiCandidateRole.LOCAL)
        ));

        Map<String, Object> payload = this.payloadFactory.toPromptPayload(context);

        assertThat(payload.get("districtBudget")).isEqualTo(4);
        assertThat(districts(payload)).hasSize(2);
        assertThat(pois(districts(payload).get(0)).stream().map(poi -> poi.get("poiId")))
                .containsExactlyInAnyOrder("chain-a", "chain-b", "chain-c");
        assertThat(pois(districts(payload).get(1)).stream().map(poi -> poi.get("poiId")))
                .containsExactly("gap-d");
    }

    private void applySampledCandidates(
            RouteGenerationContext context,
            List<PoiDiversitySampler.RankedPoi> rankedCandidates,
            int maxCount
    ) {
        context.setPoiCandidates(this.sampler.sample(context, rankedCandidates, maxCount).stream()
                .map(PoiDiversitySampler.RankedPoi::candidate)
                .toList());
    }

    private static List<PoiDiversitySampler.RankedPoi> nearAnchors(int count) {
        List<PoiDiversitySampler.RankedPoi> rankedCandidates = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rankedCandidates.add(ranked(
                    "near-" + index,
                    String.format("121.000%d", index % 10),
                    1000d + index,
                    0.40d,
                    10d - index,
                    "SHOPPING",
                    PoiCandidateRole.ANCHOR
            ));
        }
        return rankedCandidates;
    }

    private static PoiDiversitySampler.RankedPoi ranked(
            String poiId,
            String longitude,
            double distanceMeters,
            double cleanQuality,
            double linearScore,
            String category,
            PoiCandidateRole role
    ) {
        return new PoiDiversitySampler.RankedPoi(
                poi(poiId, longitude, category, role),
                score(poiId, distanceMeters, cleanQuality, linearScore)
        );
    }

    private static PoiCandidateDTO poi(String poiId, String longitude, PoiCandidateRole role) {
        return poi(poiId, longitude, "LOCAL", role);
    }

    private static PoiCandidateDTO poi(String poiId, String longitude, String category, PoiCandidateRole role) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                poiId,
                category,
                role,
                new GeoPointDTO(new BigDecimal(longitude), new BigDecimal("31.0000")),
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
                15000d,
                distanceMeters / 15000d,
                0d,
                0d,
                0d,
                linearScore
        );
    }

    private static RouteGenerationContext context(TransportProfile transportProfile, int durationMinutes) {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(transportProfile, durationMinutes)
        );
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "测试区域",
                point("121.0000"),
                22000,
                List.of()
        ));
        return context;
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
        center.setLongitudeGcj02(new BigDecimal("121.0000"));
        center.setLatitudeGcj02(new BigDecimal("31.0000"));
        return center;
    }

    private static GeoPointDTO point(String longitude) {
        return new GeoPointDTO(new BigDecimal(longitude), new BigDecimal("31.0000"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> districts(Map<String, Object> payload) {
        return (List<Map<String, Object>>) payload.get("districts");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pois(Map<String, Object> district) {
        return (List<Map<String, Object>>) district.get("pois");
    }

    private static List<String> payloadPoiIds(Map<String, Object> payload) {
        return districts(payload).stream()
                .flatMap(district -> pois(district).stream())
                .map(poi -> (String) poi.get("poiId"))
                .toList();
    }
}
