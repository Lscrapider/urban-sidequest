package com.urbansidequest.backend.handler.route.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.TransitFacilityDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.MealWindow;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.po.PoiSemanticMappingPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlanner;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

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
        LlmRoutePromptPayloadFactory factory = new LlmRoutePromptPayloadFactory(
                new RouteDistrictPlanner(),
                new PoiSemanticResolver()
        );

        Map<String, Object> payload = factory.toPromptPayload(context);

        assertThat(new ArrayList<>(payload.keySet())).containsExactly(
                "request",
                "mealWindowDefinitions",
                "transportPolicy",
                "districtBudget",
                "districtOrder",
                "districtDistanceMatrix",
                "districts"
        );
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

    @Test
    void derivesRouteRoleHintsFromSemanticCapabilities() {
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
        context.setPoiCandidates(List.of(poiWithTypecode("multi-role", "121.0000", "31.0000", "050100")));
        context.setPoiSemanticMappings(List.of(mapping("050100")));
        LlmRoutePromptPayloadFactory factory = new LlmRoutePromptPayloadFactory(
                new RouteDistrictPlanner(),
                new PoiSemanticResolver()
        );

        Map<String, Object> payload = factory.toPromptPayload(context);

        List<String> roleHints = districts(payload).stream()
                .flatMap(district -> pois(district).stream())
                .filter(poi -> "multi-role".equals(poi.get("poiId")))
                .findFirst()
                .map(poi -> (List<String>) poi.get("routeRoleHints"))
                .orElseThrow();
        assertThat(roleHints).contains("MEAL", "REST", "LOCAL");
    }

    @Test
    void resolvesSemanticMappingWhenAmapReturnsMultipleTypecodes() {
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
                poiWithTypecode("mixed-coffee-shop", "121.0000", "31.0000", "061100|050500")
        ));
        context.setPoiSemanticMappings(List.of(mapping(
                "050500",
                "DRINK",
                "DRINK",
                List.of("COFFEE"),
                false,
                true,
                false,
                false,
                false
        )));
        LlmRoutePromptPayloadFactory factory = new LlmRoutePromptPayloadFactory(
                new RouteDistrictPlanner(),
                new PoiSemanticResolver()
        );

        Map<String, Object> payload = factory.toPromptPayload(context);

        Map<String, Object> poi = districts(payload).stream()
                .flatMap(district -> pois(district).stream())
                .filter(item -> "mixed-coffee-shop".equals(item.get("poiId")))
                .findFirst()
                .orElseThrow();
        assertThat(poi)
                .containsEntry("primaryCategoryGroup", "DRINK")
                .containsEntry("restCandidate", true);
        assertThat((Collection<String>) poi.get("poiTagHits")).containsExactly("COFFEE");
    }

    @Test
    void rendersUserPromptPreviewForTagSystemV3Inputs() throws Exception {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(RouteGoal.QUIET, TransportProfile.WALK_TAXI)
        );
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "上海安静探索测试区",
                point("121.0000", "31.0000"),
                15000,
                List.of()
        ));
        context.setPoiCandidates(List.of(
                poiWithDetails(
                        "sichuan-hotpot",
                        "蜀味小馆",
                        "FOOD",
                        PoiCandidateRole.LOCAL,
                        "121.0000",
                        "31.0000",
                        "050100",
                        "餐饮服务;中餐厅",
                        "川菜",
                        "火锅",
                        8800,
                        List.of("FOOD_SICHUAN"),
                        "兴趣召回:FOOD_SICHUAN；召回计划:FOOD_CHINESE",
                        false
                ),
                poiWithDetails(
                        "coffee-rest",
                        "街角咖啡",
                        "COFFEE",
                        PoiCandidateRole.REST,
                        "121.0010",
                        "31.0000",
                        "050500",
                        "餐饮服务;咖啡厅",
                        "咖啡",
                        "甜品",
                        3200,
                        List.of("COFFEE"),
                        "兴趣召回:COFFEE",
                        false
                ).withTransitDetails(
                        List.of(new TransitFacilityDTO("SUBWAY", "静安寺站", 260)),
                        null,
                        null
                ),
                poiWithDetails(
                        "quiet-park",
                        "安静口袋公园",
                        "SCENIC",
                        PoiCandidateRole.ANCHOR,
                        "121.0600",
                        "31.0000",
                        "110100",
                        "风景名胜;公园广场",
                        "公园",
                        "安静",
                        null,
                        List.of(),
                        "召回计划:SCENIC",
                        true
                )
        ));
        context.setPoiSemanticMappings(List.of(
                mapping(
                        "050100",
                        "FOOD",
                        "FOOD",
                        List.of("FOOD_CHINESE", "LOCAL"),
                        true,
                        true,
                        true,
                        false,
                        false
                ),
                mapping(
                        "050500",
                        "DRINK",
                        "DRINK",
                        List.of("COFFEE"),
                        false,
                        true,
                        false,
                        false,
                        false
                ),
                mapping(
                        "110100",
                        "SCENIC",
                        "SCENIC",
                        List.of("SCENIC"),
                        false,
                        false,
                        false,
                        true,
                        true
                )
        ));

        String prompt = promptComposer().buildUserPrompt(context);

        assertThat(prompt)
                .contains("输入数据：")
                .contains("\"departureTime\":\"2026-06-17T10:00:00\"")
                .doesNotContain("\"departureTime\":\"2026-06-17T02:00:00Z\"")
                .contains("\"routeGoal\":\"QUIET\"")
                .contains("\"mealWindows\":[\"LUNCH\"]")
                .contains("\"mealWindowDefinitions\"")
                .contains("\"routeGoalPolicy\"")
                .contains("优先选择安静")
                .contains("\"districtDistanceMatrix\"")
                .contains("\"primaryCategoryGroup\":\"FOOD\"")
                .contains("\"poiTagHits\":[\"FOOD_CHINESE\",\"LOCAL\"]")
                .contains("\"routeRoleHints\":[\"MEAL\",\"REST\",\"LOCAL\"]")
                .contains("\"typecode\":\"050100\"")
                .contains("\"mealCandidate\":true")
                .contains("\"restCandidate\":true")
                .contains("\"localExperienceCandidate\":true")
                .contains("\"avgPriceCent\":8800")
                .contains("\"primaryCategoryGroup\":\"DRINK\"")
                .contains("\"routeRoleHints\":[\"REST\"]")
                .contains("\"transitAccessibility\":\"HIGH\"")
                .contains("\"semanticTags\":[\"PHOTO_FRIENDLY\",\"QUIET\"]")
                .contains("根据 primaryCategoryGroup、poiTagHits、semanticTags")
                .contains("mealCandidate=true 或 routeRoleHints 含 MEAL")
                .contains("不要连续安排 3 个及以上高度同质的 POI")
                .contains("相同 typecode、相同 rawType")
                .doesNotContain("根据 category、role、tags、features")
                .doesNotContain("category=FOOD 或 role=MEAL")
                .doesNotContain("不要求 tags 额外包含")
                .doesNotContain("\"amapPoiId\"")
                .doesNotContain("\"address\"")
                .doesNotContain("\"category\":")
                .doesNotContain("\"tags\":")
                .doesNotContain("\"role\":")
                .doesNotContain("\"features\"")
                .doesNotContain("\"reasonSeed\"")
                .doesNotContain("LOW_BUDGET");
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
        return baseParam(RouteGoal.STEADY, TransportProfile.WALK_TAXI);
    }

    private static RouteGenerateParam baseParam(RouteGoal routeGoal, TransportProfile transportProfile) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setCenter(center());
        param.setDepartureTime(LocalDateTime.of(2026, 6, 17, 10, 0));
        param.setDurationMinutes(360);
        param.setTransportProfile(transportProfile);
        param.setRouteGoal(routeGoal);
        param.setBudgetLevel(BudgetLevel.LOW);
        param.setRouteCityName("上海市");
        param.setRouteCityAdcode("310000");
        param.setInterestTags(List.of("FOOD_SICHUAN", "COFFEE", "SCENIC"));
        param.setMealWindows(List.of(MealWindow.LUNCH));
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

    private static PoiCandidateDTO poiWithTypecode(String poiId, String longitude, String latitude, String typecode) {
        return poiWithDetails(
                poiId,
                poiId,
                "LOCAL",
                PoiCandidateRole.LOCAL,
                longitude,
                latitude,
                typecode,
                "餐饮服务;中餐厅",
                null,
                null,
                null,
                List.of("food", "local"),
                "reason",
                false
        );
    }

    private static PoiCandidateDTO poiWithDetails(
            String poiId,
            String name,
            String category,
            PoiCandidateRole role,
            String longitude,
            String latitude,
            String typecode,
            String rawType,
            String keytag,
            String rectag,
            Integer avgPriceCent,
            List<String> tags,
            String reasonSeed,
            boolean mustVisit
    ) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                name,
                category,
                role,
                point(longitude, latitude),
                "address",
                "description",
                new BigDecimal("4.6"),
                avgPriceCent,
                tags,
                List.of(),
                0,
                rawType,
                typecode,
                null,
                null,
                keytag,
                rectag,
                null,
                List.of(),
                "MEDIUM",
                null,
                mustVisit,
                reasonSeed
        );
    }

    private static PoiSemanticMappingPO mapping(String typecode) {
        return mapping(typecode, "FOOD", "FOOD", List.of("FOOD_CHINESE", "LOCAL"), true, true, true, false, false);
    }

    private static PoiSemanticMappingPO mapping(
            String typecode,
            String categoryGroup,
            String primaryCategoryGroup,
            List<String> interestTagCodes,
            boolean mealCandidate,
            boolean restCandidate,
            boolean localExperienceCandidate,
            boolean quiet,
            boolean photoFriendly
    ) {
        PoiSemanticMappingPO mapping = new PoiSemanticMappingPO();
        mapping.setExactTypecodes(List.of(typecode));
        mapping.setCategoryGroup(categoryGroup);
        mapping.setPrimaryCategoryGroup(primaryCategoryGroup);
        mapping.setInterestTagCodes(interestTagCodes);
        mapping.setMealCandidate(mealCandidate);
        mapping.setRestCandidate(restCandidate);
        mapping.setLocalExperienceCandidate(localExperienceCandidate);
        mapping.setQuiet(quiet);
        mapping.setPhotoFriendly(photoFriendly);
        return mapping;
    }

    private static GeoPointDTO point(String longitude, String latitude) {
        return new GeoPointDTO(new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private static LlmRouteCandidateComposer promptComposer() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.defaultTemplateRenderer(any())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        return new LlmRouteCandidateComposer(
                chatClientBuilder,
                new ObjectMapper().findAndRegisterModules(),
                new LlmRoutePromptPayloadFactory(new RouteDistrictPlanner(), new PoiSemanticResolver())
        );
    }
}
