package com.urbansidequest.backend.domain.vo;

import java.time.Instant;
import java.util.UUID;

public record RouteShareVO(
        UUID shareId,
        UUID requestId,
        String routeCode,
        String routeTitle,
        String cityName,
        Integer totalDurationMinutes,
        Integer totalDistanceMeters,
        Integer stopCount,
        String shareText,
        String imageUrl,
        Instant createdAt
) {
}
