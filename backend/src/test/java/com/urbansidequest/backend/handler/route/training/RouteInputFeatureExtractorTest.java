package com.urbansidequest.backend.handler.route.training;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.SegmentModeResolver;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteInputFeatureExtractorTest {

    @Test
    void detectsDinnerWindowWhenRouteCrossesMidnight() throws Exception {
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

        assertThat(routeDerivedVector)
                .containsEntry("requiresDinnerFlag", 1.0)
                .containsEntry("missingRequiredMealFlag", 1.0);
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

    private static RouteGenerateParam baseParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setDepartureTime(Instant.parse("2026-06-20T10:00:00Z"));
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

    private static RouteStopDTO stop(String stopId, int order, String name, String slotLabel) {
        return new RouteStopDTO(
                stopId,
                order,
                name,
                slotLabel,
                "SCENIC",
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
