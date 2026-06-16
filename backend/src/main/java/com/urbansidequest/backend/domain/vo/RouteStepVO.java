package com.urbansidequest.backend.domain.vo;

import java.util.List;

public record RouteStepVO(
        int order,
        String instruction,
        String roadName,
        int distanceMeters,
        int durationMinutes,
        List<GeoPointVO> polyline
) {
}
