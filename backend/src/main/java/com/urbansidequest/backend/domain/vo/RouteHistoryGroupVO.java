package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteHistoryGroupVO(
        UUID requestId,
        UUID candidateSetId,
        String areaLabel,
        Instant createdAt,
        RouteRequestStatus generationStatus,
        String generationStage,
        String activeRouteCode,
        RouteExecutionStatus executionStatus,
        List<RouteHistoryRouteSummaryVO> routes
) {
}
