package com.urbansidequest.backend.handler.route.search;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import java.util.List;
import java.util.Set;

public record RouteSearchState(
        List<PoiCandidateDTO> stops,
        Set<String> poiIds,
        Set<String> coveredInterestTags,
        int totalDurationMinutes,
        int totalDistanceMeters,
        int budgetCent,
        int score
) {
}
