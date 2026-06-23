package com.urbansidequest.backend.handler.route.step;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import com.urbansidequest.backend.handler.route.config.RouteScoringTestSupport;
import com.urbansidequest.backend.handler.route.constraint.DistrictBudgetConstraint;
import com.urbansidequest.backend.handler.route.constraint.DurationConstraint;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlanner;
import com.urbansidequest.backend.handler.route.scoring.RouteGoalScoringStrategy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoreAndSelectRoutesStepTest {

    private static final RouteScoringProperties ROUTE_SCORING_PROPERTIES = RouteScoringTestSupport.properties();

    @Test
    void returnsEmptyRoutesWhenAllRoutesFailDurationConstraint() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(120)
        );
        CandidateRouteDTO longRoute = route("A", 240, 20);
        CandidateRouteDTO shorterOvertimeRoute = route("B", 150, 10);
        context.setCandidateRoutes(List.of(longRoute, shorterOvertimeRoute));

        ScoreAndSelectRoutesStep step = new ScoreAndSelectRoutesStep(
                List.of(new DurationConstraint()),
                List.of(alwaysZeroScoringStrategy())
        );

        step.execute(context);

        assertThat(context.getSelectedRoutes()).isEmpty();
        assertThat(context.getWarnings()).anyMatch(warning -> warning.contains("没有候选路线通过后端约束"));
    }

    @Test
    void rejectsOverBudgetRouteWhenAnotherRouteFitsDistrictBudget() {
        RouteGenerationContext context = routeContextForDistrictBudget();
        CandidateRouteDTO compactRoute = route("A", List.of(stop("p1-A", "p1"), stop("p2-A", "p2")), 20);
        CandidateRouteDTO scatteredRoute = route("B", List.of(stop("p1-B", "p1"), stop("p3-B", "p3")), 90);
        context.setCandidateRoutes(List.of(scatteredRoute, compactRoute));

        ScoreAndSelectRoutesStep step = new ScoreAndSelectRoutesStep(
                List.of(new DistrictBudgetConstraint(new RouteDistrictPlanner(ROUTE_SCORING_PROPERTIES))),
                List.of(alwaysZeroScoringStrategy())
        );

        step.execute(context);

        assertThat(context.getSelectedRoutes())
                .extracting(CandidateRouteDTO::routeCode)
                .containsExactly("A");
        assertThat(context.getWarnings()).anyMatch(warning -> warning.contains("B 未通过约束"));
    }

    @Test
    void returnsEmptyRoutesWhenAllRoutesExceedDistrictBudget() {
        RouteGenerationContext context = routeContextForDistrictBudget();
        CandidateRouteDTO twoDistrictRoute = route("A", List.of(stop("p1-A", "p1"), stop("p3-A", "p3")), 20);
        CandidateRouteDTO threeDistrictRoute = route("B", List.of(stop("p1-B", "p1"), stop("p3-B", "p3"), stop("p4-B", "p4")), 90);
        context.setCandidateRoutes(List.of(threeDistrictRoute, twoDistrictRoute));

        ScoreAndSelectRoutesStep step = new ScoreAndSelectRoutesStep(
                List.of(new DistrictBudgetConstraint(new RouteDistrictPlanner(ROUTE_SCORING_PROPERTIES))),
                List.of(alwaysZeroScoringStrategy())
        );

        step.execute(context);

        assertThat(context.getSelectedRoutes())
                .extracting(CandidateRouteDTO::routeCode)
                .isEmpty();
        assertThat(context.getWarnings()).anyMatch(warning -> warning.contains("没有候选路线通过后端约束"));
    }

    private static RouteGenerateParam baseParam(int durationMinutes) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setCenter(center());
        param.setDepartureTime(LocalDateTime.of(2026, 6, 17, 10, 0));
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private static RouteGenerationContext routeContextForDistrictBudget() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(180)
        );
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "测试区域",
                point("121.0000", "31.0000"),
                5000,
                List.of()
        ));
        context.setPoiCandidates(List.of(
                poi("p1", "121.0000", "31.0000"),
                poi("p2", "121.0020", "31.0000"),
                poi("p3", "121.0300", "31.0000"),
                poi("p4", "121.0600", "31.0000")
        ));
        return context;
    }

    private static CandidateRouteDTO route(String routeCode, int durationMinutes, int score) {
        return new CandidateRouteDTO(
                routeCode,
                "路线 " + routeCode,
                "summary",
                durationMinutes,
                0,
                null,
                RiskLevel.LOW,
                "explanation",
                List.of(),
                List.of(),
                score
        );
    }

    private static CandidateRouteDTO route(String routeCode, List<RouteStopDTO> stops, int score) {
        return new CandidateRouteDTO(
                routeCode,
                "路线 " + routeCode,
                "summary",
                90,
                0,
                null,
                RiskLevel.LOW,
                "explanation",
                stops,
                List.of(),
                score
        );
    }

    private static RouteStopDTO stop(String stopId, String name) {
        return new RouteStopDTO(
                stopId,
                1,
                name,
                "景点",
                "LOCAL",
                point("121.0000", "31.0000"),
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

    private static PoiCandidateDTO poi(String poiId, String longitude, String latitude) {
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
                List.of("LOCAL"),
                List.of(),
                List.of(),
                "MEDIUM",
                false,
                "reason"
        );
    }

    private static GeoPointDTO point(String longitude, String latitude) {
        return new GeoPointDTO(new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private static GeoPointParam center() {
        GeoPointParam center = new GeoPointParam();
        center.setLongitudeGcj02(new BigDecimal("121.0000"));
        center.setLatitudeGcj02(new BigDecimal("31.0000"));
        return center;
    }

    private static RouteGoalScoringStrategy alwaysZeroScoringStrategy() {
        return new RouteGoalScoringStrategy() {
            @Override
            public boolean supports(RouteGoal routeGoal) {
                return true;
            }

            @Override
            public int score(CandidateRouteDTO route, RouteGenerationContext context) {
                return 0;
            }
        };
    }
}
