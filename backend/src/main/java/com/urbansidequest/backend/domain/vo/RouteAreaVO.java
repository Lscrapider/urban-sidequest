package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.AreaMode;
import java.util.List;

public record RouteAreaVO(
        AreaMode areaMode,
        String areaLabel,
        String cityName,
        GeoPointVO center,
        int radiusMeters,
        List<GeoPointVO> polygonGcj02,
        String description
) {
}
