package com.urbansidequest.backend.handler.route.constraint;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;

public interface RouteConstraint {

    ConstraintResult check(CandidateRouteDTO route, RouteGenerationContext context);
}
