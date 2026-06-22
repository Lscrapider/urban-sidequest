package com.urbansidequest.backend.handler.route.district;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteDistrictPlannerTest {

    @Test
    void startsOrderFromNearestMustVisitDistrictAndExpandsBudgetOnlyToMustVisitDistrictCount() {
        RouteGenerationContext context = new RouteGenerationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                baseParam(TransportProfile.WALK_ONLY, 180)
        );
        context.setArea(new RouteAreaDTO(
                AreaMode.AUTO_RADIUS,
                "测试区域",
                point("121.0000", "31.0000"),
                5000,
                List.of()
        ));
        context.setPoiCandidates(List.of(
                poi("near", "121.0000", "31.0000", false),
                poi("must-near", "121.0200", "31.0000", true),
                poi("must-far", "121.0500", "31.0000", true),
                poi("optional-far", "121.0800", "31.0000", false)
        ));

        RouteDistrictPlan plan = new RouteDistrictPlanner().plan(context);

        assertThat(plan.baseDistrictBudget()).isEqualTo(1);
        assertThat(plan.effectiveDistrictBudget()).isEqualTo(2);
        assertThat(plan.districtIdOf("must-near")).isEqualTo(plan.districtOrder().get(0));
        assertThat(plan.districtIdOf("must-far")).isNotEqualTo(plan.districtIdOf("must-near"));
        assertThat(plan.districtIdOf("optional-far")).isNotIn(plan.requiredDistrictIds());
    }

    private static RouteGenerateParam baseParam(TransportProfile transportProfile, int durationMinutes) {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setCenter(center());
        param.setDepartureTime(LocalDateTime.of(2026, 6, 17, 10, 0));
        param.setDurationMinutes(durationMinutes);
        param.setTransportProfile(transportProfile);
        param.setRouteGoal(RouteGoal.STEADY);
        return param;
    }

    private static GeoPointParam center() {
        GeoPointParam center = new GeoPointParam();
        center.setLongitudeGcj02(new BigDecimal("121.0000"));
        center.setLatitudeGcj02(new BigDecimal("31.0000"));
        return center;
    }

    private static PoiCandidateDTO poi(String poiId, String longitude, String latitude, boolean mustVisit) {
        return new PoiCandidateDTO(
                poiId,
                poiId,
                poiId,
                "LOCAL",
                mustVisit ? PoiCandidateRole.MUST_VISIT : PoiCandidateRole.LOCAL,
                point(longitude, latitude),
                "address",
                "description",
                new BigDecimal("4.6"),
                null,
                List.of("LOCAL"),
                List.of(),
                List.of(),
                "MEDIUM",
                mustVisit,
                "reason"
        );
    }

    private static GeoPointDTO point(String longitude, String latitude) {
        return new GeoPointDTO(new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
