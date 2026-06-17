package com.urbansidequest.backend.handler.route.pool;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.List;

public interface PoiPoolSelector {

    List<PoiCandidateDTO> select(RouteGenerationContext context, List<PoiCandidateDTO> candidates);
}
