package com.urbansidequest.backend.handler.route.step;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.constraint.DurationConstraint;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.scoring.RouteGoalScoringStrategy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoreAndSelectRoutesStepTest {

    @Test
    void fallsBackToShortestOvertimeRouteWhenAllRoutesFailDurationConstraint() {
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

        assertThat(context.getSelectedRoutes()).hasSize(2);
        assertThat(context.getSelectedRoutes().get(0).routeCode()).isEqualTo("A");
        assertThat(context.getSelectedRoutes().get(0).totalDurationMinutes()).isEqualTo(150);
        assertThat(context.getWarnings()).anyMatch(warning -> warning.contains("最短超时路线"));
    }

    private static RouteGenerateParam baseParam(int durationMinutes) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setDepartureTime(Instant.parse("2026-06-17T02:00:00Z"));
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
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
