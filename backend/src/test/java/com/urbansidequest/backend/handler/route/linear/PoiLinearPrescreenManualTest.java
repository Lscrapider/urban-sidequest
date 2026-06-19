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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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

    private static final int RUNS_PER_SCENARIO = 3;

    private static final List<TransportProfile> TRANSPORT_PROFILES = List.of(
            TransportProfile.WALK_SUBWAY,
            TransportProfile.WALK_BUS,
            TransportProfile.WALK_TRANSIT
    );

    private static final Path OUTPUT_PATH = Path.of(
            "target",
            "poi-linear-ranker",
            "prescreen-local-food-compare.json"
    );

    private static final List<ScenarioSpec> SCENARIOS = List.of(
            new ScenarioSpec(
                    "shanghai-bund",
                    "上海外滩固定测试范围",
                    "121.490317",
                    "31.238541",
                    3000,
                    "上海市",
                    "310000"
            ),
            new ScenarioSpec(
                    "beijing-tiananmen",
                    "北京天安门固定测试范围",
                    "116.397477",
                    "39.908692",
                    3000,
                    "北京市",
                    "110000"
            )
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
        List<Map<String, Object>> scenarioResults = new ArrayList<>();
        for (ScenarioSpec scenario : SCENARIOS) {
            List<Map<String, Object>> profileResults = new ArrayList<>();
            for (TransportProfile transportProfile : TRANSPORT_PROFILES) {
                List<Map<String, Object>> runs = new ArrayList<>();
                for (int runIndex = 1; runIndex <= RUNS_PER_SCENARIO; runIndex++) {
                    RouteGenerateParam param = fixedLocalFoodParam(scenario, transportProfile);
                    RouteGenerationContext context = new RouteGenerationContext(
                            requestIdOf(scenario, transportProfile, runIndex), USER_ID, param);

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

                    runs.add(this.resultOf(context, candidateCountBeforeSelect, runIndex, transportProfile));
                }
                profileResults.add(this.profileResultOf(transportProfile, runs));
            }
            scenarioResults.add(this.scenarioResultOf(scenario, profileResults));
        }

        this.writeResult(scenarioResults);
    }

    private static RouteGenerateParam fixedLocalFoodParam(ScenarioSpec scenario, TransportProfile transportProfile) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setAreaLabel(scenario.areaLabel());
        param.setCenter(point(scenario.longitude(), scenario.latitude()));
        param.setRadiusMeters(scenario.radiusMeters());
        param.setRouteCityName(scenario.routeCityName());
        param.setRouteCityAdcode(scenario.routeCityAdcode());
        param.setDepartureTime(Instant.parse("2026-06-20T10:00:00Z"));
        param.setDurationMinutes(240);
        param.setTransportProfile(transportProfile);
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

    private static UUID requestIdOf(ScenarioSpec scenario, TransportProfile transportProfile, int runIndex) {
        if (runIndex == 1
                && transportProfile == TransportProfile.WALK_SUBWAY
                && "shanghai-bund".equals(scenario.id())) {
            return REQUEST_ID;
        }
        String seed = "poi-linear-prescreen:" + scenario.id() + ":" + transportProfile.name() + ":" + runIndex;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> resultOf(
            RouteGenerationContext context,
            int candidateCountBeforeSelect,
            int runIndex,
            TransportProfile transportProfile
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runIndex", runIndex);
        result.put("transportProfile", transportProfile);
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
        return result;
    }

    private Map<String, Object> profileResultOf(TransportProfile transportProfile, List<Map<String, Object>> runs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transportProfile", transportProfile);
        result.put("runs", runs);
        return result;
    }

    private Map<String, Object> scenarioResultOf(ScenarioSpec scenario, List<Map<String, Object>> profileResults) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", scenario.id());
        result.put("areaLabel", scenario.areaLabel());
        result.put("center", point(scenario.longitude(), scenario.latitude()));
        result.put("radiusMeters", scenario.radiusMeters());
        result.put("routeCityName", scenario.routeCityName());
        result.put("routeCityAdcode", scenario.routeCityAdcode());
        result.put("profiles", profileResults);
        return result;
    }

    private void writeResult(List<Map<String, Object>> scenarioResults) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", USER_ID);
        result.put("runsPerScenario", RUNS_PER_SCENARIO);
        result.put("transportProfiles", TRANSPORT_PROFILES);
        result.put("scenarios", scenarioResults);

        Files.createDirectories(OUTPUT_PATH.getParent());
        this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT_PATH.toFile(), result);
    }

    private record ScenarioSpec(
            String id,
            String areaLabel,
            String longitude,
            String latitude,
            Integer radiusMeters,
            String routeCityName,
            String routeCityAdcode
    ) {
    }
}
