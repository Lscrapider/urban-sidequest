package com.urbansidequest.backend.handler.route.linear;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.step.EnrichPoiDetailsStep;
import com.urbansidequest.backend.handler.route.step.LoadInterestTagsStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiCandidatesStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiSemanticMappingsStep;
import com.urbansidequest.backend.handler.route.step.LoadUserPreferenceProfileStep;
import com.urbansidequest.backend.handler.route.step.ResolveAreaStep;
import com.urbansidequest.backend.handler.route.step.SelectPoiPoolStep;
import com.urbansidequest.backend.handler.route.step.ValidateRouteRequestStep;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 手动集成测试：固定参数跑到 POI Linear 预筛为止，不进入 LLM 路线编排。
 *
 * <p>默认不启用，避免普通 mvn test 依赖本地数据库/高德接口。需要调参时显式加
 * {@code -Durban.poi.prescreen.enabled=true} 运行；固定请求参数可复用高德 POI search 缓存。</p>
 */
@Tag("manual")
@SpringBootTest
@EnabledIfSystemProperty(named = "urban.poi.prescreen.enabled", matches = "true")
class PoiLinearPrescreenManualTest {

    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-00000000a101");

    private static final UUID USER_ID = UUID.fromString("9f3dbdb7-15a9-4643-9bf1-baae06a6cf9c");

    private static final Path OUTPUT_PATH = Path.of(
            "target",
            "poi-linear-ranker",
            "prescreen-local-food-shanghai.json"
    );

    private final ValidateRouteRequestStep validateRouteRequestStep;

    private final ResolveAreaStep resolveAreaStep;

    private final LoadInterestTagsStep loadInterestTagsStep;

    private final LoadUserPreferenceProfileStep loadUserPreferenceProfileStep;

    private final LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep;

    private final LoadPoiCandidatesStep loadPoiCandidatesStep;

    private final EnrichPoiDetailsStep enrichPoiDetailsStep;

    private final SelectPoiPoolStep selectPoiPoolStep;

    private final ObjectMapper objectMapper;

    @Autowired
    PoiLinearPrescreenManualTest(
            ValidateRouteRequestStep validateRouteRequestStep,
            ResolveAreaStep resolveAreaStep,
            LoadInterestTagsStep loadInterestTagsStep,
            LoadUserPreferenceProfileStep loadUserPreferenceProfileStep,
            LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep,
            LoadPoiCandidatesStep loadPoiCandidatesStep,
            EnrichPoiDetailsStep enrichPoiDetailsStep,
            SelectPoiPoolStep selectPoiPoolStep,
            ObjectMapper objectMapper
    ) {
        this.validateRouteRequestStep = validateRouteRequestStep;
        this.resolveAreaStep = resolveAreaStep;
        this.loadInterestTagsStep = loadInterestTagsStep;
        this.loadUserPreferenceProfileStep = loadUserPreferenceProfileStep;
        this.loadPoiSemanticMappingsStep = loadPoiSemanticMappingsStep;
        this.loadPoiCandidatesStep = loadPoiCandidatesStep;
        this.enrichPoiDetailsStep = enrichPoiDetailsStep;
        this.selectPoiPoolStep = selectPoiPoolStep;
        this.objectMapper = objectMapper;
    }

    @Test
    void savesRepeatablePoiLinearPrescreenResultWithoutLlm() throws Exception {
        RouteGenerateParam param = fixedLocalFoodParam();
        RouteGenerationContext context = new RouteGenerationContext(REQUEST_ID, USER_ID, param);

        this.validateRouteRequestStep.execute(context);
        this.resolveAreaStep.execute(context);
        this.loadInterestTagsStep.execute(context);
        this.loadUserPreferenceProfileStep.execute(context);
        this.loadPoiSemanticMappingsStep.execute(context);
        this.loadPoiCandidatesStep.execute(context);
        int candidateCountBeforeSelect = context.getPoiCandidates().size();

        this.enrichPoiDetailsStep.execute(context);
        this.selectPoiPoolStep.execute(context);

        assertThat(candidateCountBeforeSelect).isGreaterThan(0);
        assertThat(context.getPoiCandidates()).isNotEmpty();
        assertThat(context.getPoiLinearTraces()).isNotEmpty();

        this.writeResult(context, candidateCountBeforeSelect);
    }

    private static RouteGenerateParam fixedLocalFoodParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setAreaLabel("上海外滩固定测试范围");
        param.setCenter(point("121.490317", "31.238541"));
        param.setRadiusMeters(3000);
        param.setRouteCityName("上海市");
        param.setRouteCityAdcode("310000");
        param.setDepartureTime(Instant.parse("2026-06-20T10:00:00Z"));
        param.setDurationMinutes(240);
        param.setTransportProfile(TransportProfile.WALK_SUBWAY);
        param.setRouteGoal(RouteGoal.LOCAL);
        param.setBudgetLevel(BudgetLevel.NORMAL);
        param.setInterestTags(List.of("FOOD", "LOCAL", "NIGHT"));
        return param;
    }

    private static GeoPointParam point(String longitude, String latitude) {
        GeoPointParam point = new GeoPointParam();
        point.setLongitudeGcj02(new BigDecimal(longitude));
        point.setLatitudeGcj02(new BigDecimal(latitude));
        return point;
    }

    private void writeResult(RouteGenerationContext context, int candidateCountBeforeSelect) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", context.getRequestId());
        result.put("userId", context.getUserId());
        result.put("area", context.getArea());
        result.put("request", context.getGenerateParam());
        result.put("candidateCountBeforeSelect", candidateCountBeforeSelect);
        result.put("selectedCandidateCount", context.getPoiCandidates().size());
        result.put("transportSignalAvailable", context.isTransportSignalAvailable());
        result.put("warnings", context.getWarnings());
        result.put("selectedCandidates", context.getPoiCandidates());
        result.put("linearTraces", context.getPoiLinearTraces());

        Files.createDirectories(OUTPUT_PATH.getParent());
        this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT_PATH.toFile(), result);
    }
}
