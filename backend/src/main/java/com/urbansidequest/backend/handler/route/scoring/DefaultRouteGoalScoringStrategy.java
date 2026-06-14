package com.urbansidequest.backend.handler.route.scoring;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultRouteGoalScoringStrategy implements RouteGoalScoringStrategy {

    @Override
    public boolean supports(RouteGoal routeGoal) {
        return true;
    }

    @Override
    public int score(CandidateRouteDTO route, RouteGenerationContext context) {
        int durationDelta = Math.abs(context.getGenerateParam().getDurationMinutes() - route.totalDurationMinutes());
        int score = 1000 - durationDelta;
        score += route.stops().size() * 20;
        if (RiskLevel.LOW == route.riskLevel()) {
            score += 80;
        }
        score += switch (context.getGenerateParam().getRouteGoal()) {
            case STEADY -> -route.totalDistanceMeters() / 100;
            case CLASSIC -> route.stops().size() * 15;
            case LOCAL -> this.containsCategory(route, "LOCAL") || this.containsCategory(route, "FOOD")
                    ? 90
                    : 0;
            case LOW_BUDGET -> route.budgetCent() == null ? 60 : -route.budgetCent() / 1000;
            case NIGHT -> this.containsCategory(route, "NIGHT") ? 80 : 0;
            case PHOTO -> this.containsCategory(route, "SCENIC") ? 80 : 0;
        };
        return score;
    }

    private boolean containsCategory(CandidateRouteDTO route, String category) {
        return route.stops().stream().anyMatch(stop -> category.equals(stop.category()));
    }
}
