package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteHistoryGroupVO(
        UUID requestId,
        UUID candidateSetId,
        String areaLabel,
        Instant createdAt,
        String activeRouteCode,
        RouteExecutionStatus executionStatus,
        List<RouteHistoryRouteSummaryVO> routes
) {
}
