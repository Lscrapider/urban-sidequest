package com.urbansidequest.backend.provider.route;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import java.util.List;

public interface PoiCandidateProvider {

    List<PoiCandidateDTO> loadCandidates(RouteGenerationContext context);
}
