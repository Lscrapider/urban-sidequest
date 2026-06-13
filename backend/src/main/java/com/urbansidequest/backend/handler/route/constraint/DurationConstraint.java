package com.urbansidequest.backend.handler.route.constraint;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class DurationConstraint implements RouteConstraint {

    @Override
    public ConstraintResult check(CandidateRouteDTO route, RouteGenerationContext context) {
        int maxDurationMinutes = context.getGenerateParam().getDurationMinutes();
        if (route.totalDurationMinutes() > maxDurationMinutes) {
            return ConstraintResult.failed("路线总时长超过用户可用时长");
        }
        return ConstraintResult.success();
    }
}
