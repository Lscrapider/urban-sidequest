package com.urbansidequest.backend.domain.dto;

import java.math.BigDecimal;
import java.util.List;

public record PoiCandidateDTO(
        String poiId,
        String amapPoiId,
        String name,
        String category,
        GeoPointDTO location,
        BigDecimal amapRating,
        Integer avgPriceCent,
        List<String> matchedInterestTags,
        boolean mustVisit,
        String reasonSeed
) {
}
