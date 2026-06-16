package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import java.math.BigDecimal;
import java.util.List;

public record RouteStopVO(
        String stopId,
        int order,
        String name,
        String slotLabel,
        String category,
        GeoPointVO location,
        BigDecimal rating,
        int stayMinutes,
        SegmentTransportMode transportToNext,
        Integer distanceToNextMeters,
        Integer durationToNextMinutes,
        String description,
        List<String> imageUrls,
        String reason,
        String riskNote
) {
}
