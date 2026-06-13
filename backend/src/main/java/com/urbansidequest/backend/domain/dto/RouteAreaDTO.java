package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.AreaMode;
import java.util.List;

public record RouteAreaDTO(
        AreaMode areaMode,
        String areaLabel,
        GeoPointDTO center,
        int radiusMeters,
        List<GeoPointDTO> polygonGcj02
) {
}
