package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import java.math.BigDecimal;
import java.util.List;

public record PoiCandidateDTO(
        String poiId,
        String amapPoiId,
        String name,
        String category,
        PoiCandidateRole role,
        GeoPointDTO location,
        BigDecimal amapRating,
        Integer avgPriceCent,
        List<String> matchedInterestTags,
        boolean mustVisit,
        String reasonSeed
) {
}
