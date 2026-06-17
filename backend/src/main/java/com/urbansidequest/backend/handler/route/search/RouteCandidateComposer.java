package com.urbansidequest.backend.handler.route.search;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.List;

public interface RouteCandidateComposer {

    List<CandidateRouteDTO> composeRoutes(RouteGenerationContext context);
}
