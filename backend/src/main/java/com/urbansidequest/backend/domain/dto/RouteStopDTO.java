package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import java.math.BigDecimal;
import java.util.List;

public record RouteStopDTO(
        String stopId,
        int order,
        String name,
        String slotLabel,
        String category,
        GeoPointDTO location,
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
