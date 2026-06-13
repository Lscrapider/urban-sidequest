package com.urbansidequest.backend.handler.route.constraint;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.service.route.RouteGenerationContext;

public interface RouteConstraint {

    ConstraintResult check(CandidateRouteDTO route, RouteGenerationContext context);
}
