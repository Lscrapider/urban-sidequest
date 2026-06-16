package com.urbansidequest.backend.domain.dto;

import java.util.List;
import java.util.Map;

public record AmapPoiSearchQueryDTO(
        String searchType,
        GeoPointDTO center,
        Integer radiusMeters,
        List<GeoPointDTO> polygon,
        List<String> types,
        List<String> keywords,
        int pageNum,
        int pageSize
) {

    public Map<String, Object> toCacheParams() {
        return Map.of(
                "searchType", this.searchType(),
                "center", this.center() == null ? "" : this.center().toString(),
                "radiusMeters", this.radiusMeters() == null ? 0 : this.radiusMeters(),
                "polygon", this.polygon() == null ? List.of() : this.polygon(),
                "types", this.types() == null ? List.of() : this.types(),
                "keywords", this.keywords() == null ? List.of() : this.keywords(),
                "pageNum", this.pageNum(),
                "pageSize", this.pageSize()
        );
    }
}
