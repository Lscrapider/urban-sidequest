package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FilterCalibratedRoutesStep implements RouteGenerationStep {

    private final RouteScoringProperties routeScoringProperties;

    public FilterCalibratedRoutesStep(RouteScoringProperties routeScoringProperties) {
        this.routeScoringProperties = routeScoringProperties;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getSelectedRoutes().isEmpty()) {
            return;
        }
        int requestDurationMinutes = Math.max(1, context.getGenerateParam().getDurationMinutes());
        double hardLimit = requestDurationMinutes + Math.max(
                this.routeScoringProperties.routeConstraintDouble("duration-hard-overrun-minutes"),
                requestDurationMinutes * this.routeScoringProperties.routeConstraintDouble("duration-hard-overrun-ratio")
        );
        List<CandidateRouteDTO> filteredRoutes = context.getSelectedRoutes().stream()
                .filter(route -> this.isDisplayable(route, context, hardLimit))
                .toList();
        if (filteredRoutes.isEmpty()) {
            context.addWarning("所有候选路线均因校准后硬超限被过滤");
        }
        context.setSelectedRoutes(filteredRoutes);
    }

    private boolean isDisplayable(CandidateRouteDTO route, RouteGenerationContext context, double hardLimit) {
        if (!this.hasValidStopSegmentShape(route)) {
            context.addWarning(route.routeCode() + " 线校准后 stops/segments 结构不匹配，已丢弃该候选路线");
            return false;
        }
        if (route.totalDurationMinutes() > hardLimit) {
            context.addWarning(route.routeCode() + " 线校准后总时长超过硬上限，已丢弃该候选路线");
            return false;
        }
        return true;
    }

    private boolean hasValidStopSegmentShape(CandidateRouteDTO route) {
        if (route.stops() == null || route.stops().isEmpty()) {
            return false;
        }
        int segmentCount = route.segments() == null ? 0 : route.segments().size();
        return segmentCount == route.stops().size() - 1;
    }
}
