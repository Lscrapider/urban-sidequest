package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.RiskLevel;
import java.util.List;

public record GeneratedRouteVO(
        String routeCode,
        String title,
        String summary,
        int totalDurationMinutes,
        int totalDistanceMeters,
        Integer budgetCent,
        RiskLevel riskLevel,
        String explanation,
        List<RouteStopVO> stops
) {
}
