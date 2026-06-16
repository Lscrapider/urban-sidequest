package com.urbansidequest.backend.domain.dto;

import java.util.List;

public record RouteStepDTO(
        int order,
        String instruction,
        String roadName,
        int distanceMeters,
        int durationMinutes,
        List<GeoPointDTO> polyline
) {
}
