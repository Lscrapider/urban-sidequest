package com.urbansidequest.backend.handler.route.training;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteSegmentDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.BudgetLevel;
import com.urbansidequest.backend.domain.enums.MealWindow;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteSegmentSource;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import com.urbansidequest.backend.domain.po.PoiSemanticMappingPO;
import com.urbansidequest.backend.domain.po.RoutePreferenceRawSnapshotPO;
import com.urbansidequest.backend.handler.route.SegmentModeResolver;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import com.urbansidequest.backend.handler.route.config.RouteScoringTestSupport;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import com.urbansidequest.backend.handler.route.support.RouteSegmentDurationEstimator;
import com.urbansidequest.backend.manage.RoutePreferenceRawSnapshotManage;
import com.urbansidequest.backend.manage.RoutePreferenceTrainingSampleManage;
import com.urbansidequest.backend.service.impl.RoutePreferenceFeatureRebuildServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoutePreferenceRawSnapshotRebuildTest {

    private static final RouteScoringProperties ROUTE_SCORING_PROPERTIES = RouteScoringTestSupport.properties();

    @Test
    void rebuildsSameFeatureSnapshotFromFrozenRawSnapshotWhenAlgorithmIsUnchanged() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RouteInputFeatureExtractor extractor = extractor(objectMapper);
        RouteGenerationContext context = routeGenerationContext();
        CandidateRouteDTO route = context.getSelectedRoutes().get(0);
        RouteInputFeatureSnapshot onlineSnapshot = extractor.extractCandidateSet(context).get(route.routeCode());

        RoutePreferenceRawSnapshotBuilder builder = new RoutePreferenceRawSnapshotBuilder();
        RoutePreferenceRawSnapshotPayload payload = builder.build(context);
        RoutePreferenceRawSnapshotPO rawSnapshotPO = RoutePreferenceRawSnapshotPO.fromPayload(payload, objectMapper);

        RoutePreferenceRawSnapshotManage rawSnapshotManage = mock(RoutePreferenceRawSnapshotManage.class);
        RoutePreferenceTrainingSampleManage trainingSampleManage = mock(RoutePreferenceTrainingSampleManage.class);
        when(rawSnapshotManage.findByCandidateSetId(context.getCandidateSetId()))
                .thenReturn(Optional.of(rawSnapshotPO));

        RoutePreferenceFeatureRebuildServiceImpl rebuildService = new RoutePreferenceFeatureRebuildServiceImpl(
                rawSnapshotManage,
                trainingSampleManage,
                extractor,
                new RoutePreferenceRawSnapshotRestorer(objectMapper)
        );

        int rebuiltCount = rebuildService.rebuildByCandidateSetId(context.getCandidateSetId());

        ArgumentCaptor<RouteInputFeatureSnapshot> snapshotCaptor = ArgumentCaptor.forClass(RouteInputFeatureSnapshot.class);
        verify(trainingSampleManage).upsertRebuiltSample(
                eq(context.getCandidateSetId()),
                eq(context.getRequestId()),
                eq(context.getUserId()),
                eq(route.routeCode()),
                snapshotCaptor.capture()
        );
        assertThat(rebuiltCount).isEqualTo(1);
        assertSameFeatureSnapshot(objectMapper, snapshotCaptor.getValue(), onlineSnapshot);
    }

    private static void assertSameFeatureSnapshot(
            ObjectMapper objectMapper,
            RouteInputFeatureSnapshot actual,
            RouteInputFeatureSnapshot expected
    ) throws Exception {
        assertThat(actual.featureSchemaVersion()).isEqualTo(expected.featureSchemaVersion());
        assertThat(objectMapper.readTree(actual.stopMatrixJson())).isEqualTo(objectMapper.readTree(expected.stopMatrixJson()));
        assertThat(objectMapper.readTree(actual.segmentMatrixJson())).isEqualTo(objectMapper.readTree(expected.segmentMatrixJson()));
        assertThat(objectMapper.readTree(actual.routeDerivedVectorJson())).isEqualTo(objectMapper.readTree(expected.routeDerivedVectorJson()));
        assertThat(objectMapper.readTree(actual.contextCrossVectorJson())).isEqualTo(objectMapper.readTree(expected.contextCrossVectorJson()));
        assertThat(objectMapper.readTree(actual.intraSetVectorJson())).isEqualTo(objectMapper.readTree(expected.intraSetVectorJson()));
        assertThat(objectMapper.readTree(actual.contextJson())).isEqualTo(objectMapper.readTree(expected.contextJson()));
    }

    private static RouteGenerationContext routeGenerationContext() {
        UUID requestId = UUID.randomUUID();
        UUID candidateSetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RouteGenerationContext context = new RouteGenerationContext(requestId, candidateSetId, userId, baseParam());
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "人民广场周边",
                point("121.4737", "31.2304"),
                2400,
                List.of(point("121.4600", "31.2200"), point("121.4870", "31.2200"), point("121.4870", "31.2400"))
        ));
        context.setRouteWeather(RouteWeatherDTO.unavailable());
        context.setUserPreferenceProfile(new UserPreferenceProfileDTO(
                new BigDecimal("0.80"),
                new BigDecimal("0.40"),
                new BigDecimal("0.30"),
                new BigDecimal("0.70"),
                new BigDecimal("0.90"),
                "v1",
                false,
                Map.of("SCENIC", new BigDecimal("0.80"), "LOCAL", new BigDecimal("0.60"))
        ));
        context.setInterestTagCatalog(List.of(tag("SCENIC"), tag("LOCAL")));
        context.setInterestTags(List.of(tag("SCENIC"), tag("LOCAL")));
        context.setPoiSemanticMappings(List.of(semanticMapping()));
        context.setPoiCandidates(List.of(
                candidate("p1", "110101"),
                candidate("p2", "110101"),
                candidate("p3", "110101")
        ));
        context.setPoiLinearTraces(List.of(
                trace("p1", 0.90, 0.70),
                trace("p2", 0.60, 0.30),
                trace("p3", 0.50, 0.20)
        ));
        context.setSegmentCosts(List.of(
                new SegmentCostDTO("p1", "p2", SegmentTransportMode.WALK, 900, 12, 900, 0, "步行"),
                new SegmentCostDTO("p2", "p3", SegmentTransportMode.SUBWAY, 4200, 18, 400, 1, "地铁")
        ));
        context.setSelectedRoutes(List.of(route()));
        context.addWarning("晚高峰可能拥堵");
        return context;
    }

    private static RouteGenerateParam baseParam() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setAreaLabel("人民广场周边");
        param.setCenter(pointParam("121.4737", "31.2304"));
        param.setRadiusMeters(2400);
        param.setRouteCityName("上海");
        param.setRouteCityAdcode("310000");
        param.setDepartureTime(LocalDateTime.of(2026, 6, 23, 14, 0));
        param.setDurationMinutes(240);
        param.setTransportProfile(TransportProfile.WALK_SUBWAY);
        param.setRouteGoal(RouteGoal.LOCAL);
        param.setBudgetLevel(BudgetLevel.NORMAL);
        param.setInterestTags(List.of("SCENIC", "LOCAL"));
        param.setMealWindows(List.of(MealWindow.DINNER));
        return param;
    }

    private static CandidateRouteDTO route() {
        return new CandidateRouteDTO(
                "A",
                "路线 A",
                "从广场到本地街区",
                180,
                5100,
                12000,
                RiskLevel.LOW,
                "兼顾经典和本地体验",
                List.of(
                        stop("p1-A", 0, "人民广场", "起点", "ANCHOR", null),
                        stop("p2-A", 1, "弄堂咖啡", "休息", "REST", null),
                        stop("p3-A", 2, "本地餐馆", "晚餐", "MEAL", "DINNER")
                ),
                List.of(
                        segment(1, "p1-A", "p2-A", SegmentTransportMode.WALK, 900, 12),
                        segment(2, "p2-A", "p3-A", SegmentTransportMode.SUBWAY, 4200, 18)
                ),
                88
        );
    }

    private static RouteInputFeatureExtractor extractor(ObjectMapper objectMapper) {
        return new RouteInputFeatureExtractor(
                objectMapper,
                new PoiSemanticResolver(),
                new SegmentModeResolver(ROUTE_SCORING_PROPERTIES),
                new RouteSegmentDurationEstimator(ROUTE_SCORING_PROPERTIES),
                ROUTE_SCORING_PROPERTIES
        );
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
                point("121.4737", "31.2304"),
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

    private static RouteSegmentDTO segment(
            int order,
            String originStopId,
            String destinationStopId,
            SegmentTransportMode mode,
            int distanceMeters,
            int durationMinutes
    ) {
        return new RouteSegmentDTO(
                order,
                originStopId,
                destinationStopId,
                mode,
                distanceMeters,
                durationMinutes,
                List.of(),
                List.of(),
                mode.name(),
                RouteSegmentSource.AMAP_DIRECT
        );
    }

    private static PoiCandidateDTO candidate(String poiId, String typecode) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                poiId,
                "SCENIC",
                PoiCandidateRole.ANCHOR,
                point("121.4737", "31.2304"),
                "上海市黄浦区",
                "description",
                new BigDecimal("4.6"),
                4000,
                List.of("SCENIC", "LOCAL"),
                List.of(),
                0,
                "风景名胜;公园广场;城市广场",
                typecode,
                null,
                null,
                "城市广场",
                "本地体验",
                300,
                List.of(),
                "HIGH",
                TransitLookupStatus.SUCCESS,
                false,
                "reason"
        );
    }

    private static PoiLinearTraceDTO trace(String poiId, double interestScore, double riskCost) {
        return new PoiLinearTraceDTO(
                poiId,
                poiId,
                TransitLookupStatus.SUCCESS,
                true,
                interestScore,
                0.70,
                0.60,
                0.50,
                0.20,
                0.10,
                riskCost,
                0.40,
                600d,
                2400d,
                0.25,
                0.10,
                0.80,
                0.20,
                72d
        );
    }

    private static InterestTagCatalogPO tag(String code) {
        InterestTagCatalogPO tag = new InterestTagCatalogPO();
        tag.setTagCode(code);
        tag.setDisplayName(code);
        tag.setCategoryGroup(code);
        tag.setCatalogVersion("tag_catalog_v1_1");
        tag.setEnabled(true);
        return tag;
    }

    private static PoiSemanticMappingPO semanticMapping() {
        PoiSemanticMappingPO mapping = new PoiSemanticMappingPO();
        mapping.setMappingCode("SCENIC_LOCAL");
        mapping.setDisplayName("本地景点");
        mapping.setExactTypecodes(List.of("110101"));
        mapping.setCategoryGroup("SCENIC");
        mapping.setPrimaryCategoryGroup("SCENIC");
        mapping.setInterestTagCodes(List.of("SCENIC", "LOCAL"));
        mapping.setClassic(true);
        mapping.setLocal(true);
        mapping.setPhotoFriendly(true);
        mapping.setNightFriendly(false);
        mapping.setQuiet(false);
        mapping.setHiddenGem(true);
        mapping.setMealCandidate(false);
        mapping.setRestCandidate(false);
        mapping.setLocalExperienceCandidate(true);
        mapping.setWeatherSensitivity(new BigDecimal("0.20"));
        mapping.setMappingVersion("poi_semantic_v1_1");
        mapping.setPriority(10);
        mapping.setEnabled(true);
        return mapping;
    }

    private static GeoPointDTO point(String longitude, String latitude) {
        return new GeoPointDTO(new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private static GeoPointParam pointParam(String longitude, String latitude) {
        GeoPointParam point = new GeoPointParam();
        point.setLongitudeGcj02(new BigDecimal(longitude));
        point.setLatitudeGcj02(new BigDecimal(latitude));
        return point;
    }
}
