package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import java.util.List;
import java.util.UUID;

public record RouteGenerationVO(
        UUID requestId,
        UUID candidateSetId,
        UUID userId,
        RouteRequestStatus status,
        RouteAreaVO area,
        List<GeneratedRouteVO> routes,
        List<String> warnings,
        String activeRouteCode,
        RouteExecutionStatus executionStatus
) {
}
