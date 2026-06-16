package com.urbansidequest.backend.handler.route.constraint;

import cn.hutool.core.collection.CollUtil;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.param.MustVisitPointParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MustVisitConstraint implements RouteConstraint {

    @Override
    public ConstraintResult check(CandidateRouteDTO route, RouteGenerationContext context) {
        if (CollUtil.isEmpty(context.getGenerateParam().getMustVisitPoints())) {
            return ConstraintResult.success();
        }
        Set<String> routeStopNames = route.stops().stream()
                .map(stop -> stop.name())
                .collect(Collectors.toSet());
        for (MustVisitPointParam mustVisitPoint : context.getGenerateParam().getMustVisitPoints()) {
            if (!routeStopNames.contains(mustVisitPoint.getName())) {
                return ConstraintResult.failed("必去点未被安排：" + mustVisitPoint.getName());
            }
        }
        return ConstraintResult.success();
    }
}
