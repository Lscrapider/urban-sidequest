package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.RiskLevel;
import java.util.List;

public record CandidateRouteDTO(
        String routeCode,
        String title,
        String summary,
        int totalDurationMinutes,
        int totalDistanceMeters,
        Integer budgetCent,
        RiskLevel riskLevel,
        String explanation,
        List<RouteStopDTO> stops,
        int score
) {
}
