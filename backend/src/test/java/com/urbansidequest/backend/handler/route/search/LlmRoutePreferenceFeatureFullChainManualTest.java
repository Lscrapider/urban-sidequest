package com.urbansidequest.backend.handler.route.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.constant.DateTimeFormatConstant;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.step.BuildCandidateRoutesStep;
import com.urbansidequest.backend.handler.route.step.CalibrateSelectedRouteSegmentsStep;
import com.urbansidequest.backend.handler.route.step.EnrichPoiDetailsStep;
import com.urbansidequest.backend.handler.route.step.LoadInterestTagsStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiCandidatesStep;
import com.urbansidequest.backend.handler.route.step.LoadPoiSemanticMappingsStep;
import com.urbansidequest.backend.handler.route.step.LoadRouteWeatherStep;
import com.urbansidequest.backend.handler.route.step.LoadUserPreferenceProfileStep;
import com.urbansidequest.backend.handler.route.step.ResolveAreaStep;
import com.urbansidequest.backend.handler.route.step.ScoreAndSelectRoutesStep;
import com.urbansidequest.backend.handler.route.step.SelectPoiPoolStep;
import com.urbansidequest.backend.handler.route.step.ValidateRouteRequestStep;
import com.urbansidequest.backend.handler.route.support.MealWindowSupport;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureExtractor;
import com.urbansidequest.backend.handler.route.training.RouteInputFeatureSnapshot;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceFeatureSchema;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 手动集成测试：真实调用高德 POI、高德路线和 LLM，输出 route x 特征快照。
 *
 * <p>默认不启用，避免普通测试消耗外部额度。需要检查完整 route x 链路时显式加
 * {@code -Durban.route-x.full-chain.enabled=true}。</p>
 */
@Tag("manual")
@SpringBootTest
@EnabledIfSystemProperty(named = "urban.route-x.full-chain.enabled", matches = "true")
class LlmRoutePreferenceFeatureFullChainManualTest {

    private static final UUID USER_ID = UUID.fromString("9f3dbdb7-15a9-4643-9bf1-baae06a6cf9c");

    private static final int EXPECTED_RECALL_COUNT = 100;

    private static final int EXPECTED_PROMPT_POI_COUNT = 40;

    private static final int EXPECTED_ROUTE_COUNT = 5;

    private static final Path OUTPUT_DIR = Path.of("target", "route-x-full-chain-v3");

    private final ValidateRouteRequestStep validateRouteRequestStep;

    private final ResolveAreaStep resolveAreaStep;

    private final LoadInterestTagsStep loadInterestTagsStep;

    private final LoadUserPreferenceProfileStep loadUserPreferenceProfileStep;

    private final LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep;

    private final LoadRouteWeatherStep loadRouteWeatherStep;

    private final LoadPoiCandidatesStep loadPoiCandidatesStep;

    private final EnrichPoiDetailsStep enrichPoiDetailsStep;

    private final SelectPoiPoolStep selectPoiPoolStep;

    private final BuildCandidateRoutesStep buildCandidateRoutesStep;

    private final ScoreAndSelectRoutesStep scoreAndSelectRoutesStep;

    private final CalibrateSelectedRouteSegmentsStep calibrateSelectedRouteSegmentsStep;

    private final RouteInputFeatureExtractor routeInputFeatureExtractor;

    private final LlmRoutePromptPayloadFactory promptPayloadFactory;

    private final LlmRouteCandidateComposer llmRouteCandidateComposer;

    private final ObjectMapper objectMapper;

    @BeforeAll
    static void cleanOutputRoot() throws IOException {
        cleanDirectory(OUTPUT_DIR);
    }

    @Autowired
    LlmRoutePreferenceFeatureFullChainManualTest(
            ValidateRouteRequestStep validateRouteRequestStep,
            ResolveAreaStep resolveAreaStep,
            LoadInterestTagsStep loadInterestTagsStep,
            LoadUserPreferenceProfileStep loadUserPreferenceProfileStep,
            LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep,
            LoadRouteWeatherStep loadRouteWeatherStep,
            LoadPoiCandidatesStep loadPoiCandidatesStep,
            EnrichPoiDetailsStep enrichPoiDetailsStep,
            SelectPoiPoolStep selectPoiPoolStep,
            BuildCandidateRoutesStep buildCandidateRoutesStep,
            ScoreAndSelectRoutesStep scoreAndSelectRoutesStep,
            CalibrateSelectedRouteSegmentsStep calibrateSelectedRouteSegmentsStep,
            RouteInputFeatureExtractor routeInputFeatureExtractor,
            LlmRoutePromptPayloadFactory promptPayloadFactory,
            LlmRouteCandidateComposer llmRouteCandidateComposer,
            ObjectMapper objectMapper
    ) {
        this.validateRouteRequestStep = validateRouteRequestStep;
        this.resolveAreaStep = resolveAreaStep;
        this.loadInterestTagsStep = loadInterestTagsStep;
        this.loadUserPreferenceProfileStep = loadUserPreferenceProfileStep;
        this.loadPoiSemanticMappingsStep = loadPoiSemanticMappingsStep;
        this.loadRouteWeatherStep = loadRouteWeatherStep;
        this.loadPoiCandidatesStep = loadPoiCandidatesStep;
        this.enrichPoiDetailsStep = enrichPoiDetailsStep;
        this.selectPoiPoolStep = selectPoiPoolStep;
        this.buildCandidateRoutesStep = buildCandidateRoutesStep;
        this.scoreAndSelectRoutesStep = scoreAndSelectRoutesStep;
        this.calibrateSelectedRouteSegmentsStep = calibrateSelectedRouteSegmentsStep;
        this.routeInputFeatureExtractor = routeInputFeatureExtractor;
        this.promptPayloadFactory = promptPayloadFactory;
        this.llmRouteCandidateComposer = llmRouteCandidateComposer;
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("manualScenarios")
    void writesRoutePreferenceFeaturesAfterLlmComposition(ManualScenario scenario) throws Exception {
        Path outputDir = OUTPUT_DIR.resolve(scenario.code());
        RouteGenerationContext context = new RouteGenerationContext(requestId(scenario), USER_ID, scenario.param());

        this.validateRouteRequestStep.execute(context);
        this.resolveAreaStep.execute(context);
        this.loadInterestTagsStep.execute(context);
        this.loadUserPreferenceProfileStep.execute(context);
        this.loadPoiSemanticMappingsStep.execute(context);
        this.loadRouteWeatherStep.execute(context);

        this.loadPoiCandidatesStep.execute(context);
        List<PoiCandidateDTO> recalledCandidates = List.copyOf(context.getPoiCandidates());
        assertThat(recalledCandidates).hasSize(scenario.expectedRecallCount());

        this.enrichPoiDetailsStep.execute(context);
        assertThat(context.getPoiCandidates()).hasSize(scenario.expectedRecallCount());

        this.selectPoiPoolStep.execute(context);
        List<PoiCandidateDTO> selectedCandidates = List.copyOf(context.getPoiCandidates());
        assertThat(selectedCandidates).hasSize(EXPECTED_PROMPT_POI_COUNT);

        Map<String, Object> promptPayload = this.promptPayloadFactory.toPromptPayload(context);
        String userPrompt = this.llmRouteCandidateComposer.buildUserPrompt(context);
        assertThat(promptPoiCount(promptPayload)).isEqualTo(EXPECTED_PROMPT_POI_COUNT);

        this.buildCandidateRoutesStep.execute(context);
        List<CandidateRouteDTO> llmRoutes = List.copyOf(context.getCandidateRoutes());
        assertThat(llmRoutes).hasSize(EXPECTED_ROUTE_COUNT);

        this.scoreAndSelectRoutesStep.execute(context);
        List<CandidateRouteDTO> selectedRoutesBeforeCalibration = List.copyOf(context.getSelectedRoutes());
        assertThat(selectedRoutesBeforeCalibration).isNotEmpty();

        this.calibrateSelectedRouteSegmentsStep.execute(context);
        List<CandidateRouteDTO> calibratedSelectedRoutes = List.copyOf(context.getSelectedRoutes());
        assertThat(calibratedSelectedRoutes).isNotEmpty();

        List<Map<String, Object>> featureSnapshots = this.featureSnapshots(calibratedSelectedRoutes, context);
        assertThat(featureSnapshots).hasSameSizeAs(calibratedSelectedRoutes);
        assertThat(featureSnapshots).allSatisfy(feature -> assertThat(feature)
                .containsEntry("featureSchemaVersion", RoutePreferenceFeatureSchema.VERSION)
                .doesNotContainKeys("districtId", "districtBudget", "districtDistanceMatrix", "districtOrder"));

        this.writeOutputs(
                outputDir,
                context,
                selectedCandidates,
                promptPayload,
                userPrompt,
                llmRoutes,
                selectedRoutesBeforeCalibration,
                calibratedSelectedRoutes,
                featureSnapshots
        );
    }

    @Test
    void writesRoutePreferenceFeaturesForPythonRequestsJsonScenario() throws Exception {
        this.writesRoutePreferenceFeaturesAfterLlmComposition(this.pythonRequestsJsonScenario());
    }

    private static Stream<Arguments> manualScenarios() {
        return Stream.of(
                Arguments.of(scenario(
                        "01_quiet_taxi_low",
                        "安静低预算 Taxi 全日",
                        TransportProfile.WALK_TAXI,
                        RouteGoal.QUIET,
                        BudgetLevel.LOW,
                        LocalDateTime.of(2026, 6, 20, 18, 0),
                        420,
                        List.of("FOOD_CHINESE", "COFFEE", "SCENIC", "LOCAL")
                )),
                Arguments.of(scenario(
                        "02_classic_walk_halfday",
                        "经典步行半日",
                        TransportProfile.WALK_ONLY,
                        RouteGoal.CLASSIC,
                        BudgetLevel.NORMAL,
                        LocalDateTime.of(2026, 6, 20, 9, 0),
                        240,
                        List.of("SCENIC", "CULTURE", "MUSEUM", "PHOTO")
                )),
                Arguments.of(scenario(
                        "03_local_subway_halfday",
                        "本地生活地铁半日",
                        TransportProfile.WALK_SUBWAY,
                        RouteGoal.LOCAL,
                        BudgetLevel.NORMAL,
                        LocalDateTime.of(2026, 6, 20, 12, 0),
                        360,
                        List.of("LOCAL", "FOOD_LOCAL_FLAVOR", "COFFEE", "SHOPPING")
                )),
                Arguments.of(scenario(
                        "04_night_bus_evening",
                        "夜游公交晚间",
                        TransportProfile.WALK_BUS,
                        RouteGoal.NIGHT,
                        BudgetLevel.NORMAL,
                        LocalDateTime.of(2026, 6, 20, 18, 0),
                        300,
                        List.of("NIGHT", "ENTERTAINMENT", "FOOD_CHINESE", "COFFEE")
                )),
                Arguments.of(scenario(
                        "05_photo_bike_full",
                        "拍照骑行地铁全日",
                        TransportProfile.BIKE_SUBWAY,
                        RouteGoal.PHOTO,
                        BudgetLevel.NORMAL,
                        LocalDateTime.of(2026, 6, 20, 14, 30),
                        420,
                        List.of("PHOTO", "SCENIC", "CULTURE", "COFFEE")
                ))
        );
    }

    private ManualScenario pythonRequestsJsonScenario() throws IOException {
        JsonNode root = this.objectMapper.readTree(pythonRequestsJsonPath().toFile());
        assertThat(root.isArray()).isTrue();
        assertThat(root).isNotEmpty();
        JsonNode job = root.get(0);
        RouteGenerateParam param = this.objectMapper.convertValue(job.get("request"), RouteGenerateParam.class);
        if (job.hasNonNull("persona")) {
            param.setUserPreferenceProfileOverride(
                    this.objectMapper.convertValue(job.get("persona"), UserPreferenceProfileDTO.class)
            );
        }
        return new ManualScenario(
                "python_requests_json_xian_dayanta",
                "Python requests.json 西安大雁塔复现",
                param,
                80
        );
    }

    private static Path pythonRequestsJsonPath() {
        Path projectRootPath = Path.of("scripts", "route_preference_simulator", "requests.json");
        if (Files.exists(projectRootPath)) {
            return projectRootPath;
        }
        return Path.of("..", "scripts", "route_preference_simulator", "requests.json");
    }

    private static ManualScenario scenario(
            String code,
            String title,
            TransportProfile transportProfile,
            RouteGoal routeGoal,
            BudgetLevel budgetLevel,
            LocalDateTime departureTime,
            int durationMinutes,
            List<String> interestTags
    ) {
        return new ManualScenario(code, title, fixedParam(
                title,
                transportProfile,
                routeGoal,
                budgetLevel,
                departureTime,
                durationMinutes,
                interestTags
        ));
    }

    private static RouteGenerateParam fixedParam(
            String title,
            TransportProfile transportProfile,
            RouteGoal routeGoal,
            BudgetLevel budgetLevel,
            LocalDateTime departureTime,
            int durationMinutes,
            List<String> interestTags
    ) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.MANUAL_POLYGON);
        param.setAreaLabel("上海中心城区 " + title + " Route X 测试范围");
        param.setAreaPolygonGcj02(List.of(
                point("121.440000", "31.200000"),
                point("121.525000", "31.200000"),
                point("121.525000", "31.275000"),
                point("121.440000", "31.275000"),
                point("121.440000", "31.200000")
        ));
        param.setRouteCityName("上海市");
        param.setRouteCityAdcode("310000");
        param.setDepartureTime(departureTime);
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(transportProfile);
        param.setRouteGoal(routeGoal);
        param.setBudgetLevel(budgetLevel);
        param.setInterestTags(interestTags);
        param.setMealWindows(MealWindowSupport.feasibleMealWindows(param));
        return param;
    }

    private List<Map<String, Object>> featureSnapshots(
            List<CandidateRouteDTO> routes,
            RouteGenerationContext context
    ) throws Exception {
        List<Map<String, Object>> outputs = new ArrayList<>();
        for (CandidateRouteDTO route : routes) {
            RouteInputFeatureSnapshot snapshot = this.routeInputFeatureExtractor.extract(route, context);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("routeCode", route.routeCode());
            output.put("title", route.title());
            output.put("featureSchemaVersion", snapshot.featureSchemaVersion());
            output.put("stopMatrix", this.readList(snapshot.stopMatrixJson()));
            output.put("segmentMatrix", this.readList(snapshot.segmentMatrixJson()));
            output.put("routeDerivedVector", this.readMap(snapshot.routeDerivedVectorJson()));
            output.put("contextCrossVector", this.readMap(snapshot.contextCrossVectorJson()));
            output.put("contextJson", this.readMap(snapshot.contextJson()));
            output.put("rawSnapshot", snapshot);
            outputs.add(output);
        }
        return outputs;
    }

    private void writeOutputs(
            Path outputDir,
            RouteGenerationContext context,
            List<PoiCandidateDTO> selectedCandidates,
            Map<String, Object> promptPayload,
            String userPrompt,
            List<CandidateRouteDTO> llmRoutes,
            List<CandidateRouteDTO> selectedRoutesBeforeCalibration,
            List<CandidateRouteDTO> calibratedSelectedRoutes,
            List<Map<String, Object>> featureSnapshots
    ) throws Exception {
        cleanDirectory(outputDir);
        this.writeJson(outputDir, "01-request.json", this.clientRequestOutput(context));
        this.writeJson(outputDir, "02-selected-candidates.json", this.selectedCandidateOutput(selectedCandidates, promptPayload, context));
        this.writeJson(outputDir, "03-district-payload.json", promptPayload);
        Files.writeString(outputDir.resolve("04-user-prompt.txt"), userPrompt, StandardCharsets.UTF_8);
        this.writeJson(outputDir, "05-llm-candidate-routes.json", llmRoutes);
        this.writeJson(outputDir, "06-selected-routes-before-calibration.json", selectedRoutesBeforeCalibration);
        this.writeJson(outputDir, "07-calibrated-selected-routes.json", calibratedSelectedRoutes);
        this.writeJson(outputDir, "08-route-x-features.json", featureSnapshots);
        this.writeJson(outputDir, "summary.json", this.summaryOutput(
                outputDir,
                context,
                selectedCandidates,
                promptPayload,
                llmRoutes,
                selectedRoutesBeforeCalibration,
                calibratedSelectedRoutes,
                featureSnapshots
        ));
    }

    private Map<String, Object> clientRequestOutput(RouteGenerationContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        RouteGenerateParam request = context.getGenerateParam();
        output.put("areaMode", request.getAreaMode());
        output.put("areaLabel", request.getAreaLabel());
        if (request.getCenter() != null) {
            output.put("center", request.getCenter());
        }
        if (request.getRadiusMeters() != null) {
            output.put("radiusMeters", request.getRadiusMeters());
        }
        if (!request.getAreaPolygonGcj02().isEmpty()) {
            output.put("areaPolygonGcj02", request.getAreaPolygonGcj02());
        }
        if (!request.getAdminAdcodes().isEmpty()) {
            output.put("adminAdcodes", request.getAdminAdcodes());
        }
        output.put("routeCityName", request.getRouteCityName());
        output.put("routeCityAdcode", request.getRouteCityAdcode());
        output.put("departureTime", request.getDepartureTime() == null
                ? null
                : DateTimeFormatConstant.BEIJING_LOCAL_DATE_TIME_FORMATTER.format(request.getDepartureTime()));
        output.put("durationMinutes", request.getDurationMinutes());
        output.put("transportProfile", request.getTransportProfile());
        output.put("routeGoal", request.getRouteGoal());
        output.put("budgetLevel", request.getBudgetLevel());
        output.put("interestTags", request.getInterestTags());
        output.put("mealWindows", request.getMealWindows());
        if (!request.getMustVisitPoints().isEmpty()) {
            output.put("mustVisitPoints", request.getMustVisitPoints());
        }
        if (request.getUserPreferenceProfileOverride() != null) {
            output.put("userPreferenceProfileOverride", request.getUserPreferenceProfileOverride());
        }
        return output;
    }

    private List<Map<String, Object>> selectedCandidateOutput(
            List<PoiCandidateDTO> selectedCandidates,
            Map<String, Object> promptPayload,
            RouteGenerationContext context
    ) {
        Map<String, String> districtIdsByPoiId = this.districtIdsByPoiId(promptPayload);
        Map<String, Object> traceByPoiId = context.getPoiLinearTraces().stream()
                .collect(Collectors.toMap(
                        trace -> trace.poiId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<Map<String, Object>> output = new ArrayList<>();
        for (PoiCandidateDTO candidate : selectedCandidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("poi", candidate);
            item.put("districtId", districtIdsByPoiId.get(candidate.poiId()));
            item.put("linearTrace", traceByPoiId.get(candidate.poiId()));
            output.add(item);
        }
        return output;
    }

    private Map<String, Object> summaryOutput(
            Path outputDir,
            RouteGenerationContext context,
            List<PoiCandidateDTO> selectedCandidates,
            Map<String, Object> promptPayload,
            List<CandidateRouteDTO> llmRoutes,
            List<CandidateRouteDTO> selectedRoutesBeforeCalibration,
            List<CandidateRouteDTO> calibratedSelectedRoutes,
            List<Map<String, Object>> featureSnapshots
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("outputDirectory", outputDir.toAbsolutePath().toString());
        output.put("requestId", context.getRequestId());
        output.put("candidateSetId", context.getCandidateSetId());
        output.put("userId", context.getUserId());
        output.put("warnings", context.getWarnings());
        output.put("selectedCandidateCount", selectedCandidates.size());
        output.put("promptPoiCount", promptPoiCount(promptPayload));
        output.put("llmCandidateRouteCount", llmRoutes.size());
        output.put("selectedRouteCountBeforeCalibration", selectedRoutesBeforeCalibration.size());
        output.put("calibratedSelectedRouteCount", calibratedSelectedRoutes.size());
        output.put("featureSnapshotCount", featureSnapshots.size());
        output.put("featureSchemaVersions", featureSnapshots.stream()
                .map(feature -> feature.get("featureSchemaVersion"))
                .distinct()
                .toList());
        output.put("districtBudget", promptPayload.get("districtBudget"));
        output.put("districtOrder", promptPayload.get("districtOrder"));
        output.put("districtDistanceMatrix", promptPayload.get("districtDistanceMatrix"));
        output.put("routeSummaries", this.routeSummaries(calibratedSelectedRoutes, featureSnapshots));
        output.put("selectedCategoryCounts", countBy(selectedCandidates, PoiCandidateDTO::category));
        output.put("selectedRoleCounts", countBy(selectedCandidates, candidate -> candidate.role().name()));
        output.put("priceSummary", this.priceSummary(selectedCandidates));
        return output;
    }

    private List<Map<String, Object>> routeSummaries(
            List<CandidateRouteDTO> routes,
            List<Map<String, Object>> featureSnapshots
    ) {
        Map<String, Map<String, Object>> featuresByRouteCode = featureSnapshots.stream()
                .collect(Collectors.toMap(
                        feature -> String.valueOf(feature.get("routeCode")),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<Map<String, Object>> output = new ArrayList<>();
        for (CandidateRouteDTO route : routes) {
            Map<String, Object> feature = featuresByRouteCode.get(route.routeCode());
            Map<String, Object> routeDerivedVector = mapValue(feature, "routeDerivedVector");
            Map<String, Object> contextCrossVector = mapValue(feature, "contextCrossVector");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("routeCode", route.routeCode());
            item.put("title", route.title());
            item.put("score", route.score());
            item.put("stopCount", route.stops().size());
            item.put("segmentCount", route.segments().size());
            item.put("totalDurationMinutes", route.totalDurationMinutes());
            item.put("totalDistanceMeters", route.totalDistanceMeters());
            item.put("budgetCent", route.budgetCent());
            item.put("interestCoverageRatio", routeDerivedVector.get("interestCoverageRatio"));
            item.put("categoryDiversityRatio", routeDerivedVector.get("categoryDiversityRatio"));
            item.put("dominantCategoryRatio", routeDerivedVector.get("dominantCategoryRatio"));
            item.put("mealStopCountNorm", routeDerivedVector.get("mealStopCountNorm"));
            item.put("restStopCountNorm", routeDerivedVector.get("restStopCountNorm"));
            item.put("missingRequiredMealFlag", routeDerivedVector.get("missingRequiredMealFlag"));
            item.put("budgetPressure", routeDerivedVector.get("budgetPressure"));
            item.put("missingPriceRatio", routeDerivedVector.get("missingPriceRatio"));
            item.put("totalDistanceNorm", routeDerivedVector.get("totalDistanceNorm"));
            item.put("maxSegmentDistanceNorm", routeDerivedVector.get("maxSegmentDistanceNorm"));
            item.put("transferSegmentRatio", routeDerivedVector.get("transferSegmentRatio"));
            item.put("timeBudgetUsageRatio", routeDerivedVector.get("timeBudgetUsageRatio"));
            item.put("timeBudgetUnderuse", routeDerivedVector.get("timeBudgetUnderuse"));
            item.put("timeBudgetOveruse", routeDerivedVector.get("timeBudgetOveruse"));
            item.put("physicalTravelDistanceRatio", routeDerivedVector.get("physicalTravelDistanceRatio"));
            item.put("scheduledTravelDistanceRatio", routeDerivedVector.get("scheduledTravelDistanceRatio"));
            item.put("privateMotorTravelDistanceRatio", routeDerivedVector.get("privateMotorTravelDistanceRatio"));
            item.put("physicalTravelDistanceNorm", routeDerivedVector.get("physicalTravelDistanceNorm"));
            item.put("scheduledTravelDistanceNorm", routeDerivedVector.get("scheduledTravelDistanceNorm"));
            item.put("privateMotorTravelDistanceNorm", routeDerivedVector.get("privateMotorTravelDistanceNorm"));
            item.put("travelBucketSwitchCountNorm", routeDerivedVector.get("travelBucketSwitchCountNorm"));
            item.put("fallbackAmapRatio", routeDerivedVector.get("fallbackAmapRatio"));
            item.put("straightLineFallbackRatio", routeDerivedVector.get("straightLineFallbackRatio"));
            item.put("goalLocalMatch", contextCrossVector.get("goalLocalMatch"));
            item.put("goalClassicMatch", contextCrossVector.get("goalClassicMatch"));
            item.put("goalQuietMatch", contextCrossVector.get("goalQuietMatch"));
            item.put("goalPhotoMatch", contextCrossVector.get("goalPhotoMatch"));
            item.put("goalNightMatch", contextCrossVector.get("goalNightMatch"));
            item.put("profileActualModeFitRatio", contextCrossVector.get("profileActualModeFitRatio"));
            output.add(item);
        }
        return output;
    }

    private Map<String, Object> priceSummary(List<PoiCandidateDTO> candidates) {
        List<Integer> prices = candidates.stream()
                .map(PoiCandidateDTO::avgPriceCent)
                .filter(price -> price != null)
                .sorted()
                .toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("knownPriceCount", prices.size());
        output.put("missingPriceCount", candidates.size() - prices.size());
        output.put("minAvgPriceCent", prices.isEmpty() ? null : prices.get(0));
        output.put("maxAvgPriceCent", prices.isEmpty() ? null : prices.get(prices.size() - 1));
        output.put("knownAvgPriceCents", prices);
        return output;
    }

    private Map<String, String> districtIdsByPoiId(Map<String, Object> promptPayload) {
        Map<String, String> districtIdsByPoiId = new LinkedHashMap<>();
        for (Map<String, Object> district : districts(promptPayload)) {
            String districtId = String.valueOf(district.get("districtId"));
            for (Map<String, Object> poi : pois(district)) {
                districtIdsByPoiId.put(String.valueOf(poi.get("poiId")), districtId);
            }
        }
        return districtIdsByPoiId;
    }

    private Map<String, Object> readMap(String json) throws Exception {
        return this.objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> readList(String json) throws Exception {
        return this.objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private void writeJson(Path outputDir, String fileName, Object value) throws Exception {
        this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputDir.resolve(fileName).toFile(), value);
    }

    private static UUID requestId(ManualScenario scenario) {
        return UUID.nameUUIDFromBytes(("route-x-full-chain-" + scenario.code()).getBytes(StandardCharsets.UTF_8));
    }

    private static GeoPointParam point(String longitude, String latitude) {
        GeoPointParam point = new GeoPointParam();
        point.setLongitudeGcj02(new BigDecimal(longitude));
        point.setLatitudeGcj02(new BigDecimal(latitude));
        return point;
    }

    private static int promptPoiCount(Map<String, Object> promptPayload) {
        return districts(promptPayload).stream()
                .mapToInt(district -> pois(district).size())
                .sum();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> districts(Map<String, Object> promptPayload) {
        return (List<Map<String, Object>>) promptPayload.get("districts");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pois(Map<String, Object> district) {
        return (List<Map<String, Object>>) district.get("pois");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static Map<String, Long> countBy(List<PoiCandidateDTO> candidates, Function<PoiCandidateDTO, String> keyMapper) {
        return candidates.stream()
                .collect(Collectors.groupingBy(
                        candidate -> {
                            String key = keyMapper.apply(candidate);
                            return key == null || key.isBlank() ? "UNKNOWN" : key;
                        },
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    private static void cleanDirectory(Path outputDir) throws IOException {
        if (Files.exists(outputDir)) {
            try (Stream<Path> paths = Files.walk(outputDir)) {
                for (Path path : paths
                        .sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(outputDir))
                        .toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(outputDir);
    }

    private record ManualScenario(String code, String title, RouteGenerateParam param, int expectedRecallCount) {

        private ManualScenario(String code, String title, RouteGenerateParam param) {
            this(code, title, param, EXPECTED_RECALL_COUNT);
        }

        @Override
        public String toString() {
            return this.code + " - " + this.title;
        }
    }
}
