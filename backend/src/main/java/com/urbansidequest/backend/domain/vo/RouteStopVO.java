package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;

public record RouteStopVO(
        String stopId,
        int order,
        String name,
        String category,
        GeoPointVO location,
        int stayMinutes,
        SegmentTransportMode transportToNext,
        Integer distanceToNextMeters,
        Integer durationToNextMinutes,
        String reason,
        String riskNote
) {
}
