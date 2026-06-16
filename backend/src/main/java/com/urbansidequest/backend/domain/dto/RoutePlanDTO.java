package com.urbansidequest.backend.domain.dto;

import java.util.List;

public record RoutePlanDTO(
        int distanceMeters,
        int durationMinutes,
        List<GeoPointDTO> polyline,
        List<RouteStepDTO> steps,
        String summary,
        String rawPayload
) {
}
