package com.urbansidequest.backend.provider.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.urbansidequest.backend.api.amap.AmapApi;
import com.urbansidequest.backend.domain.dto.AmapPoiSearchQueryDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.domain.po.PoiRecallPlanConfigPO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.manage.AmapPoiSearchCacheManage;
import com.urbansidequest.backend.manage.PoiRecallPlanConfigManage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AmapPoiCandidateProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void expandsEventKeywordsIntoSeparatePlansWhileKeepingAllTypecodes() {
        AmapApi amapApi = mock(AmapApi.class);
        AmapPoiSearchCacheManage cacheManage = mock(AmapPoiSearchCacheManage.class);
        PoiRecallPlanConfigManage planConfigManage = mock(PoiRecallPlanConfigManage.class);
        AmapPoiCandidateProvider provider = new AmapPoiCandidateProvider(
                amapApi,
                cacheManage,
                planConfigManage,
                this.objectMapper
        );
        RouteGenerationContext context = context();
        AtomicInteger responseIndex = new AtomicInteger();
        List<AmapPoiSearchQueryDTO> capturedQueries = new ArrayList<>();

        when(amapApi.isAvailable()).thenReturn(true);
        when(cacheManage.findValidResponseJson(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(Optional.empty());
        when(planConfigManage.findEnabledInterestPlansByTagCodes(List.of("EVENT")))
                .thenReturn(List.of(eventPlan("INTEREST_EVENT_CONCERT", List.of("演唱会", "live"))));
        when(planConfigManage.findEnabledPlansByPlanTypes(List.of("BACKUP")))
                .thenReturn(List.of(backupPlan()));
        when(amapApi.searchPoi(any())).thenAnswer(invocation -> {
            AmapPoiSearchQueryDTO query = invocation.getArgument(0);
            capturedQueries.add(query);
            return this.poiResponse("poi-" + responseIndex.incrementAndGet());
        });

        provider.loadCandidates(context);

        List<AmapPoiSearchQueryDTO> eventKeywordQueries = capturedQueries.stream()
                .filter(query -> !query.keywords().isEmpty())
                .toList();
        assertThat(eventKeywordQueries).hasSize(2);
        assertThat(eventKeywordQueries.stream().map(query -> query.keywords().get(0)))
                .containsExactlyInAnyOrder("演唱会", "live");
        assertThat(eventKeywordQueries)
                .allSatisfy(query -> {
                    assertThat(query.keywords()).hasSize(1);
                    assertThat(query.types()).containsExactlyInAnyOrder("220104", "080101", "080105");
                });
    }

    private static PoiRecallPlanConfigPO eventPlan(String planCode, List<String> keywords) {
        PoiRecallPlanConfigPO config = new PoiRecallPlanConfigPO();
        config.setPlanCode(planCode);
        config.setPlanType("INTEREST_TAG");
        config.setTagCode("EVENT");
        config.setAmapTypeCodes(List.of("220104", "080101", "080105"));
        config.setAmapKeywords(keywords);
        config.setRoleHint(PoiCandidateRole.ANCHOR.name());
        config.setCategoryGroupHint("EVENT");
        config.setIntentTags(List.of("EVENT"));
        config.setReasonSeed("匹配兴趣：活动/演出");
        return config;
    }

    private static PoiRecallPlanConfigPO backupPlan() {
        PoiRecallPlanConfigPO config = new PoiRecallPlanConfigPO();
        config.setPlanCode("BACKUP_SCENIC");
        config.setPlanType("BACKUP");
        config.setAmapTypeCodes(List.of("110101"));
        config.setRoleHint(PoiCandidateRole.BACKUP.name());
        config.setCategoryGroupHint("SCENIC");
        config.setIntentTags(List.of());
        config.setReasonSeed("用于异常替换和路线兜底");
        return config;
    }

    private JsonNode poiResponse(String poiId) {
        ObjectNode response = this.objectMapper.createObjectNode();
        response.put("status", "1");
        ArrayNode pois = response.putArray("pois");
        ObjectNode poi = pois.addObject();
        poi.put("id", poiId);
        poi.put("name", "测试 POI " + poiId);
        poi.put("location", "121.4737,31.2304");
        poi.put("type", "测试类型");
        poi.put("typecode", "220104");
        return response;
    }

    private static RouteGenerationContext context() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setCenter(center());
        param.setRadiusMeters(5000);
        param.setDepartureTime(LocalDateTime.of(2026, 6, 22, 9, 0));
        param.setDurationMinutes(120);
        param.setTransportProfile(TransportProfile.WALK_TAXI);
        param.setRouteGoal(RouteGoal.STEADY);
        param.setInterestTags(List.of("EVENT"));

        RouteGenerationContext context = new RouteGenerationContext(UUID.randomUUID(), UUID.randomUUID(), param);
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "测试区域",
                new GeoPointDTO(new BigDecimal("121.4737"), new BigDecimal("31.2304")),
                5000,
                List.of()
        ));
        context.setInterestTags(List.of(eventTag()));
        return context;
    }

    private static InterestTagCatalogPO eventTag() {
        InterestTagCatalogPO tag = new InterestTagCatalogPO();
        tag.setTagCode("EVENT");
        tag.setDisplayName("活动/演出");
        tag.setCategoryGroup("EVENT");
        return tag;
    }

    private static GeoPointParam center() {
        GeoPointParam point = new GeoPointParam();
        point.setLongitudeGcj02(new BigDecimal("121.4737"));
        point.setLatitudeGcj02(new BigDecimal("31.2304"));
        return point;
    }
}
