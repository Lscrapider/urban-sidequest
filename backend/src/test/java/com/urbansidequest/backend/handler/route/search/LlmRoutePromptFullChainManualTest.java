package com.urbansidequest.backend.handler.route.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.constant.DateTimeFormatConstant;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 手动集成测试：真实跑到 LLM prompt 输入前，不调用 LLM。
 *
 * <p>默认不启用，避免普通测试消耗高德额度或依赖本地数据库。需要检查完整链路时显式加
 * {@code -Durban.llm.prompt.full-chain.enabled=true}。</p>
 */
@Tag("manual")
@SpringBootTest
@EnabledIfSystemProperty(named = "urban.llm.prompt.full-chain.enabled", matches = "true")
class LlmRoutePromptFullChainManualTest {

    private static final UUID USER_ID = UUID.fromString("9f3dbdb7-15a9-4643-9bf1-baae06a6cf9c");

    private static final int EXPECTED_RECALL_COUNT = 100;

    private static final int EXPECTED_PROMPT_POI_COUNT = 40;

    private static final Path OUTPUT_DIR = Path.of("target", "llm-route-prompt-full-chain-v3");

    private final ValidateRouteRequestStep validateRouteRequestStep;

    private final ResolveAreaStep resolveAreaStep;

    private final LoadInterestTagsStep loadInterestTagsStep;

    private final LoadUserPreferenceProfileStep loadUserPreferenceProfileStep;

    private final LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep;

    private final LoadPoiCandidatesStep loadPoiCandidatesStep;

    private final EnrichPoiDetailsStep enrichPoiDetailsStep;

    private final SelectPoiPoolStep selectPoiPoolStep;

    private final LlmRoutePromptPayloadFactory promptPayloadFactory;

    private final LlmRouteCandidateComposer llmRouteCandidateComposer;

    private final ObjectMapper objectMapper;

    @BeforeAll
    static void cleanOutputRoot() throws IOException {
        cleanDirectory(OUTPUT_DIR);
    }

    @Autowired
    LlmRoutePromptFullChainManualTest(
            ValidateRouteRequestStep validateRouteRequestStep,
            ResolveAreaStep resolveAreaStep,
            LoadInterestTagsStep loadInterestTagsStep,
            LoadUserPreferenceProfileStep loadUserPreferenceProfileStep,
            LoadPoiSemanticMappingsStep loadPoiSemanticMappingsStep,
            LoadPoiCandidatesStep loadPoiCandidatesStep,
            EnrichPoiDetailsStep enrichPoiDetailsStep,
            SelectPoiPoolStep selectPoiPoolStep,
            LlmRoutePromptPayloadFactory promptPayloadFactory,
            LlmRouteCandidateComposer llmRouteCandidateComposer,
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
        this.promptPayloadFactory = promptPayloadFactory;
        this.llmRouteCandidateComposer = llmRouteCandidateComposer;
        this.objectMapper = objectMapper;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("manualScenarios")
    void writesFullChainPromptInputFromAmapRecallToSelectedPool(ManualScenario scenario) throws Exception {
        Path outputDir = OUTPUT_DIR.resolve(scenario.code());
        RouteGenerationContext context = new RouteGenerationContext(requestId(scenario), USER_ID, scenario.param());

        this.validateRouteRequestStep.execute(context);
        this.resolveAreaStep.execute(context);
        this.loadInterestTagsStep.execute(context);
        this.loadUserPreferenceProfileStep.execute(context);
        this.loadPoiSemanticMappingsStep.execute(context);

        this.loadPoiCandidatesStep.execute(context);
        List<PoiCandidateDTO> recalledCandidates = List.copyOf(context.getPoiCandidates());
        assertThat(recalledCandidates).hasSize(EXPECTED_RECALL_COUNT);

        this.enrichPoiDetailsStep.execute(context);
        List<PoiCandidateDTO> enrichedCandidates = List.copyOf(context.getPoiCandidates());
        assertThat(enrichedCandidates).hasSize(EXPECTED_RECALL_COUNT);

        this.selectPoiPoolStep.execute(context);
        List<PoiCandidateDTO> selectedCandidates = List.copyOf(context.getPoiCandidates());
        assertThat(selectedCandidates).hasSize(EXPECTED_PROMPT_POI_COUNT);
        assertThat(context.getPoiLinearTraces()).hasSize(EXPECTED_RECALL_COUNT);

        Map<String, Object> promptPayload = this.promptPayloadFactory.toPromptPayload(context);
        String userPrompt = this.llmRouteCandidateComposer.buildUserPrompt(context);
        Map<String, Object> clientRequestOutput = this.clientRequestOutput(context);
        assertThat(clientRequestOutput.get("interestTags")).isEqualTo(scenario.param().getInterestTags());
        assertThat(clientRequestOutput)
                .doesNotContainKeys(
                        "resolvedArea",
                        "interestTagCatalog",
                        "poiSemanticMappings",
                        "amapTypecodes",
                        "amapKeywords",
                        "mustVisitPoints"
                );
        assertThat(districts(promptPayload)).isNotEmpty();
        if (districts(promptPayload).size() > 1) {
            assertThat((Map<?, ?>) promptPayload.get("districtDistanceMatrix")).isNotEmpty();
        }
        assertThat(userPrompt)
                .contains("\"transportProfile\":\"" + scenario.param().getTransportProfile().name() + "\"")
                .contains("\"routeGoal\":\"" + scenario.param().getRouteGoal().name() + "\"")
                .contains("\"routeGoalPolicy\"")
                .contains("\"districtDistanceMatrix\"")
                .contains("\"districtBudget\":")
                .contains("\"pois\":[")
                .doesNotContain("\"amapPoiId\"")
                .doesNotContain("\"typecode\"")
                .doesNotContain("\"address\"")
                .doesNotContain("\"category\":")
                .doesNotContain("\"tags\":")
                .doesNotContain("\"role\":")
                .doesNotContain("\"features\"")
                .doesNotContain("\"reasonSeed\"");
        assertThat(promptPoiCount(promptPayload)).isEqualTo(EXPECTED_PROMPT_POI_COUNT);

        this.writeOutputs(outputDir, context, recalledCandidates, enrichedCandidates, selectedCandidates, promptPayload, userPrompt);
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
        param.setAreaLabel("上海中心城区 " + title + " Prompt 测试范围");
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
        return param;
    }

    private static UUID requestId(ManualScenario scenario) {
        return UUID.nameUUIDFromBytes(("llm-route-prompt-full-chain-" + scenario.code()).getBytes(StandardCharsets.UTF_8));
    }

    private static GeoPointParam point(String longitude, String latitude) {
        GeoPointParam point = new GeoPointParam();
        point.setLongitudeGcj02(new BigDecimal(longitude));
        point.setLatitudeGcj02(new BigDecimal(latitude));
        return point;
    }

    private void writeOutputs(
            Path outputDir,
            RouteGenerationContext context,
            List<PoiCandidateDTO> recalledCandidates,
            List<PoiCandidateDTO> enrichedCandidates,
            List<PoiCandidateDTO> selectedCandidates,
            Map<String, Object> promptPayload,
            String userPrompt
    ) throws Exception {
        cleanDirectory(outputDir);
        this.writeJson(outputDir, "01-request.json", this.clientRequestOutput(context));
        this.writeJson(outputDir, "02-resolved-area.json", context.getArea());
        this.writeJson(outputDir, "03-loaded-internal-context.json", this.loadedInternalContextOutput(context));
        this.writeJson(outputDir, "04-candidates-after-recall.json", recalledCandidates);
        this.writeJson(outputDir, "05-candidates-after-enrich.json", enrichedCandidates);
        this.writeJson(outputDir, "06-linear-traces.json", context.getPoiLinearTraces());
        this.writeJson(outputDir, "07-selected-candidates.json", this.selectedCandidateOutput(selectedCandidates, promptPayload, context));
        this.writeJson(outputDir, "08-district-payload.json", promptPayload);
        Files.writeString(outputDir.resolve("09-user-prompt.txt"), userPrompt, StandardCharsets.UTF_8);
        this.writeJson(outputDir, "summary.json", this.summaryOutput(outputDir, context, recalledCandidates, enrichedCandidates, selectedCandidates, promptPayload));
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
        if (!request.getMustVisitPoints().isEmpty()) {
            output.put("mustVisitPoints", request.getMustVisitPoints());
        }
        if (request.getUserPreferenceProfileOverride() != null) {
            output.put("userPreferenceProfileOverride", request.getUserPreferenceProfileOverride());
        }
        return output;
    }

    private Map<String, Object> loadedInternalContextOutput(RouteGenerationContext context) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("requestId", context.getRequestId());
        output.put("candidateSetId", context.getCandidateSetId());
        output.put("userId", context.getUserId());
        output.put("loadedInterestTagCodes", context.getInterestTags().stream()
                .map(tag -> tag.getTagCode())
                .toList());
        output.put("loadedInterestTagCatalogSize", context.getInterestTagCatalog().size());
        output.put("loadedPoiSemanticMappingSize", context.getPoiSemanticMappings().size());
        output.put("userPreferenceProfile", context.getUserPreferenceProfile());
        output.put("warnings", context.getWarnings());
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
            List<PoiCandidateDTO> recalledCandidates,
            List<PoiCandidateDTO> enrichedCandidates,
            List<PoiCandidateDTO> selectedCandidates,
            Map<String, Object> promptPayload
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("outputDirectory", outputDir.toAbsolutePath().toString());
        output.put("requestId", context.getRequestId());
        output.put("userId", context.getUserId());
        output.put("warnings", context.getWarnings());
        output.put("candidateCountAfterRecall", recalledCandidates.size());
        output.put("candidateCountAfterEnrich", enrichedCandidates.size());
        output.put("linearTraceCount", context.getPoiLinearTraces().size());
        output.put("selectedCandidateCount", selectedCandidates.size());
        output.put("promptPoiCount", promptPoiCount(promptPayload));
        output.put("transportSignalAvailable", context.isTransportSignalAvailable());
        output.put("selectedRoleCounts", countBy(selectedCandidates, candidate -> candidate.role().name()));
        output.put("selectedCategoryCounts", countBy(selectedCandidates, PoiCandidateDTO::category));
        output.put("selectedTypecodeCounts", countBy(selectedCandidates, PoiCandidateDTO::typecode));
        output.put("priceSummary", this.priceSummary(selectedCandidates));
        output.put("districtSummary", this.districtSummary(promptPayload));
        output.put("districtBudget", promptPayload.get("districtBudget"));
        output.put("districtOrder", promptPayload.get("districtOrder"));
        output.put("districtDistanceMatrix", promptPayload.get("districtDistanceMatrix"));
        output.put("topLinearTraces", context.getPoiLinearTraces().stream()
                .sorted(Comparator.comparingDouble(trace -> -trace.linearScore()))
                .limit(EXPECTED_PROMPT_POI_COUNT)
                .toList());
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

    private List<Map<String, Object>> districtSummary(Map<String, Object> promptPayload) {
        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> district : districts(promptPayload)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("districtId", district.get("districtId"));
            item.put("centroid", district.get("centroid"));
            item.put("poiCount", district.get("poiCount"));
            item.put("dominantInterests", district.get("dominantInterests"));
            item.put("poiIds", pois(district).stream()
                    .map(poi -> poi.get("poiId"))
                    .toList());
            output.add(item);
        }
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

    private void writeJson(Path outputDir, String fileName, Object value) throws Exception {
        this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputDir.resolve(fileName).toFile(), value);
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

    private record ManualScenario(String code, String title, RouteGenerateParam param) {

        @Override
        public String toString() {
            return this.code + " - " + this.title;
        }
    }
}
