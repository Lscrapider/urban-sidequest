package com.urbansidequest.backend.domain.dto;

import java.util.List;

public record RouteStepDTO(
        Integer order,
        String instruction,
        String roadName,
        Integer distanceMeters,
        Integer durationMinutes,
        List<GeoPointDTO> polyline
) {
}
