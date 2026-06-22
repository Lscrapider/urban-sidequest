package com.urbansidequest.backend.handler.route.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.MealWindow;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.SegmentModeResolver;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteInputFeatureExtractorTest {

    @Test
    void readsDinnerRequirementFromSelectedMealWindows() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RouteInputFeatureExtractor extractor = new RouteInputFeatureExtractor(
                objectMapper,
                new PoiSemanticResolver(),
                new SegmentModeResolver()
        );
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam()
        );
        context.getGenerateParam().setMealWindows(List.of(MealWindow.DINNER));

        RouteInputFeatureSnapshot snapshot = extractor.extract(routeWithoutMealStops(), context);
        Map<String, Object> routeDerivedVector = objectMapper.readValue(
                snapshot.routeDerivedVectorJson(),
                new TypeReference<>() {
                }
        );

        assertThat(routeDerivedVector)
                .containsEntry("requiresDinnerFlag", 1.0)
                .containsEntry("missingRequiredMealFlag", 1.0);
    }

    @Test
    void doesNotInferMealRequirementFromRouteTimeWhenMealWindowsAreEmpty() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RouteInputFeatureExtractor extractor = new RouteInputFeatureExtractor(
                objectMapper,
                new PoiSemanticResolver(),
                new SegmentModeResolver()
        );
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam()
        );

        RouteInputFeatureSnapshot snapshot = extractor.extract(routeWithoutMealStops(), context);
        Map<String, Object> routeDerivedVector = objectMapper.readValue(
                snapshot.routeDerivedVectorJson(),
                new TypeReference<>() {
                }
        );
        Map<String, Object> contextJson = objectMapper.readValue(
                snapshot.contextJson(),
                new TypeReference<>() {
                }
        );

        assertThat(routeDerivedVector)
                .containsEntry("requiresDinnerFlag", 0.0)
                .containsEntry("missingRequiredMealFlag", 0.0);
        assertThat(contextJson).containsEntry("mealWindows", List.of());
    }

    @Test
    void omitsDistancePollutedPoiScoreFieldsFromTrainingInput() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RouteInputFeatureExtractor extractor = new RouteInputFeatureExtractor(
                objectMapper,
                new PoiSemanticResolver(),
                new SegmentModeResolver()
        );
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam()
        );

        RouteInputFeatureSnapshot snapshot = extractor.extract(routeWithoutMealStops(), context);
        List<Map<String, Object>> stopMatrix = objectMapper.readValue(
                snapshot.stopMatrixJson(),
                new TypeReference<>() {
                }
        );
        Map<String, Object> routeDerivedVector = objectMapper.readValue(
                snapshot.routeDerivedVectorJson(),
                new TypeReference<>() {
                }
        );
        Map<String, Object> contextCrossVector = objectMapper.readValue(
                snapshot.contextCrossVectorJson(),
                new TypeReference<>() {
                }
        );

        assertThat(stopMatrix).allSatisfy(row -> assertThat(row)
                .doesNotContainKeys("linearScore", "personalizationScore"));
        assertThat(routeDerivedVector).doesNotContainKeys(
                "avgPoiLinearScore",
                "minPoiLinearScore",
                "avgPersonalizationScore"
        );
        assertThat(contextCrossVector).doesNotContainKey("profilePersonalizationAvg");
    }

    @Test
    void usesComposerRouteRoleAndMealWindowForRouteXWhenPresent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RouteInputFeatureExtractor extractor = new RouteInputFeatureExtractor(
                objectMapper,
                new PoiSemanticResolver(),
                new SegmentModeResolver()
        );
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam()
        );
        context.getGenerateParam().setMealWindows(List.of(MealWindow.DINNER));

        RouteInputFeatureSnapshot snapshot = extractor.extract(routeWithComposerDinnerStop(), context);
        List<Map<String, Object>> stopMatrix = objectMapper.readValue(
                snapshot.stopMatrixJson(),
                new TypeReference<>() {
                }
        );
        Map<String, Object> routeDerivedVector = objectMapper.readValue(
                snapshot.routeDerivedVectorJson(),
                new TypeReference<>() {
                }
        );

        assertThat(stopMatrix.get(0))
                .containsEntry("routeRole_MEAL", 1.0)
                .containsEntry("routeRole_REST", 0.0)
                .containsEntry("routeRole_LOCAL", 0.0);
        assertThat(routeDerivedVector)
                .containsEntry("requiresDinnerFlag", 1.0)
                .containsEntry("dinnerCoveredFlag", 1.0)
                .containsEntry("missingRequiredMealFlag", 0.0);
    }

    @Test
    void extractsAmapTypecodeDiversityFromCandidateTypecode() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RouteInputFeatureExtractor extractor = new RouteInputFeatureExtractor(
                objectMapper,
                new PoiSemanticResolver(),
                new SegmentModeResolver()
        );
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam()
        );
        context.setPoiCandidates(List.of(
                candidate("p1", "110105"),
                candidate("p2", " 110105 "),
                candidate("p3", null),
                candidate("p4", ""),
                candidate("p5", "110101")
        ));

        RouteInputFeatureSnapshot snapshot = extractor.extract(routeWithFiveStops(), context);
        Map<String, Object> routeDerivedVector = objectMapper.readValue(
                snapshot.routeDerivedVectorJson(),
                new TypeReference<>() {
                }
        );

        assertThat(routeDerivedVector)
                .containsEntry("amapTypecodeDiversityRatio", 0.8)
                .containsEntry("dominantAmapTypecodeRatio", 0.4)
                .containsEntry("consecutiveSameAmapTypecodeMaxNorm", 0.4)
                .containsEntry("missingAmapTypecodeRatio", 0.4);
    }

    private static RouteGenerateParam baseParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setDepartureTime(LocalDateTime.of(2026, 6, 20, 18, 0));
        param.setDurationMinutes(360);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.NIGHT);
        return param;
    }

    private static CandidateRouteDTO routeWithoutMealStops() {
        return new CandidateRouteDTO(
                "A",
                "路线 A",
                "summary",
                360,
                0,
                null,
                RiskLevel.LOW,
                "explanation",
                List.of(
                        stop("p1-A", 0, "城市地标", "景点"),
                        stop("p2-A", 1, "观景平台", "拍照")
                ),
                List.of(),
                0
        );
    }

    private static CandidateRouteDTO routeWithComposerDinnerStop() {
        return new CandidateRouteDTO(
                "A",
                "路线 A",
                "summary",
                360,
                0,
                null,
                RiskLevel.LOW,
                "explanation",
                List.of(
                        stop("p1-A", 0, "城市地标", "餐饮", "MEAL", "DINNER"),
                        stop("p2-A", 1, "观景平台", "拍照", "PHOTO", null)
                ),
                List.of(),
                0
        );
    }

    private static CandidateRouteDTO routeWithFiveStops() {
        return new CandidateRouteDTO(
                "A",
                "路线 A",
                "summary",
                360,
                0,
                null,
                RiskLevel.LOW,
                "explanation",
                List.of(
                        stop("p1-A", 0, "大慈恩寺广场", "景点"),
                        stop("p2-A", 1, "脸谱广场", "景点"),
                        stop("p3-A", 2, "未知类型点 1", "景点"),
                        stop("p4-A", 3, "未知类型点 2", "景点"),
                        stop("p5-A", 4, "曲江池遗址公园", "景点")
                ),
                List.of(),
                0
        );
    }

    private static PoiCandidateDTO candidate(String poiId, String typecode) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                poiId,
                "SCENIC",
                PoiCandidateRole.ANCHOR,
                new GeoPointDTO(new BigDecimal("121.4737"), new BigDecimal("31.2304")),
                null,
                "description",
                new BigDecimal("4.6"),
                null,
                List.of("SCENIC"),
                List.of(),
                0,
                "风景名胜;公园广场;城市广场",
                typecode,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "UNKNOWN",
                null,
                false,
                "reason"
        );
    }

    private static RouteStopDTO stop(String stopId, int order, String name, String slotLabel) {
        return stop(stopId, order, name, slotLabel, null, null);
    }

    private static RouteStopDTO stop(
            String stopId,
            int order,
            String name,
            String slotLabel,
            String routeRole,
            String intendedMealWindow
    ) {
        return new RouteStopDTO(
                stopId,
                order,
                name,
                slotLabel,
                "SCENIC",
                routeRole,
                intendedMealWindow,
                new GeoPointDTO(new BigDecimal("121.4737"), new BigDecimal("31.2304")),
                new BigDecimal("4.6"),
                45,
                SegmentTransportMode.WALK,
                null,
                null,
                "description",
                List.of(),
                "reason",
                null
        );
    }
}
