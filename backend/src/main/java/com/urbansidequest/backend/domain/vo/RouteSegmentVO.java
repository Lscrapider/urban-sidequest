package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.RouteSegmentSource;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import java.util.List;

public record RouteSegmentVO(
        int order,
        String originStopId,
        String destinationStopId,
        SegmentTransportMode mode,
        int distanceMeters,
        int durationMinutes,
        List<GeoPointVO> polyline,
        List<RouteStepVO> steps,
        String summary,
        RouteSegmentSource source
) {
}
