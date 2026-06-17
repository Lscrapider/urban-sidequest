package com.urbansidequest.backend.handler.route.pool;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaceholderPoiPoolSelector implements PoiPoolSelector {

    private static final int MAX_LLM_POI_COUNT = 40;

    @Override
    public List<PoiCandidateDTO> select(RouteGenerationContext context, List<PoiCandidateDTO> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparing(PoiCandidateDTO::mustVisit).reversed())
                .limit(MAX_LLM_POI_COUNT)
                .toList();
    }
}
