package com.urbansidequest.backend.handler.route.scoring;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;

public interface RouteGoalScoringStrategy {

    boolean supports(RouteGoal routeGoal);

    int score(CandidateRouteDTO route, RouteGenerationContext context);
}
