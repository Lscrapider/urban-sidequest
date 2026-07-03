package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.RiskLevel;

public record RouteHistoryRouteSummaryVO(
        String routeCode,
        String title,
        int totalDurationMinutes,
        int totalDistanceMeters,
        RiskLevel riskLevel
) {
}
