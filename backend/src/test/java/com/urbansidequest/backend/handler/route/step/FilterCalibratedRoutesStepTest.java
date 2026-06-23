package com.urbansidequest.backend.handler.route.step;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.RouteSegmentDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.enums.RouteSegmentSource;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.config.RouteScoringTestSupport;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilterCalibratedRoutesStepTest {

    @Test
    void filtersOnlyRoutesBeyondCalibratedHardOverrunLimit() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(100)
        );
        context.setSelectedRoutes(List.of(
                route("A", 145),
                route("B", 130)
        ));

        new FilterCalibratedRoutesStep(RouteScoringTestSupport.properties()).execute(context);

        assertThat(context.getSelectedRoutes())
                .extracting(CandidateRouteDTO::routeCode)
                .containsExactly("B");
        assertThat(context.getWarnings())
                .anyMatch(warning -> warning.contains("A 线校准后总时长超过硬上限"));
    }

    @Test
    void keepsSingleStopRouteWithNoSegmentsWhenDurationFits() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(100)
        );
        context.setSelectedRoutes(List.of(route("A", 90, List.of(stop("p1-A")))));

        new FilterCalibratedRoutesStep(RouteScoringTestSupport.properties()).execute(context);

        assertThat(context.getSelectedRoutes())
                .extracting(CandidateRouteDTO::routeCode)
                .containsExactly("A");
        assertThat(context.getWarnings()).isEmpty();
    }

    @Test
    void returnsEmptyRoutesWithWarningWhenAllRoutesAreHardFiltered() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(100)
        );
        context.setSelectedRoutes(List.of(route("A", 145), route("B", 200)));

        new FilterCalibratedRoutesStep(RouteScoringTestSupport.properties()).execute(context);

        assertThat(context.getSelectedRoutes()).isEmpty();
        assertThat(context.getWarnings())
                .anyMatch(warning -> warning.contains("所有候选路线均因校准后硬超限被过滤"));
    }

    private static RouteGenerateParam baseParam(int durationMinutes) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setDepartureTime(LocalDateTime.of(2026, 6, 17, 10, 0));
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private static CandidateRouteDTO route(String routeCode, int durationMinutes) {
        return route(
                routeCode,
                durationMinutes,
                List.of(stop("p1-" + routeCode), stop("p2-" + routeCode)),
                List.of(segment("p1-" + routeCode, "p2-" + routeCode))
        );
    }

    private static CandidateRouteDTO route(String routeCode, int durationMinutes, List<RouteStopDTO> stops) {
        return route(routeCode, durationMinutes, stops, List.of());
    }

    private static CandidateRouteDTO route(
            String routeCode,
            int durationMinutes,
            List<RouteStopDTO> stops,
            List<RouteSegmentDTO> segments
    ) {
        return new CandidateRouteDTO(
                routeCode,
                "路线 " + routeCode,
                "summary",
                durationMinutes,
                0,
                null,
                RiskLevel.LOW,
                "explanation",
                stops,
                segments,
                0
        );
    }

    private static RouteSegmentDTO segment(String originStopId, String destinationStopId) {
        return new RouteSegmentDTO(
                1,
                originStopId,
                destinationStopId,
                SegmentTransportMode.WALK,
                500,
                10,
                List.of(),
                List.of(),
                "步行约 10 分钟",
                RouteSegmentSource.AMAP_DIRECT
        );
    }

    private static RouteStopDTO stop(String stopId) {
        return new RouteStopDTO(
                stopId,
                1,
                stopId,
                "景点",
                "SCENIC",
                "LOCAL",
                null,
                null,
                null,
                45,
                null,
                null,
                null,
                "description",
                List.of(),
                "reason",
                null
        );
    }
}
