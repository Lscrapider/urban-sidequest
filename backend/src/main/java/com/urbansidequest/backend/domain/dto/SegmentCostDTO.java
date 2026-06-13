package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;

public record SegmentCostDTO(
        String originPoiId,
        String destinationPoiId,
        SegmentTransportMode mode,
        int distanceMeters,
        int durationMinutes,
        int walkDistanceMeters,
        int transferCount,
        String summary
) {
}
