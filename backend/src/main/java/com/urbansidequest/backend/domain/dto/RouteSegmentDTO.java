package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.RouteSegmentSource;
import java.util.List;

public record RouteSegmentDTO(
        int order,
        String originStopId,
        String destinationStopId,
        SegmentTransportMode mode,
        int distanceMeters,
        int durationMinutes,
        List<GeoPointDTO> polyline,
        List<RouteStepDTO> steps,
        String summary,
        RouteSegmentSource source
) {
}
