package com.urbansidequest.backend.domain.dto;

import java.util.List;

public record BaiduPlaceSearchQueryDTO(
        String searchType,
        GeoPointDTO center,
        Integer radiusMeters,
        List<GeoPointDTO> polygon,
        String query,
        String tag,
        int pageNum,
        int pageSize
) {
}
