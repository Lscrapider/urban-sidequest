package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;

public record RouteStopDTO(
        String stopId,
        int order,
        String name,
        String category,
        GeoPointDTO location,
        int stayMinutes,
        SegmentTransportMode transportToNext,
        Integer distanceToNextMeters,
        Integer durationToNextMinutes,
        String reason,
        String riskNote
) {
}
