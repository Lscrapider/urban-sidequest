package com.urbansidequest.backend.handler.route.scoring;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultRouteGoalScoringStrategy implements RouteGoalScoringStrategy {

    private final RouteScoringProperties routeScoringProperties;

    public DefaultRouteGoalScoringStrategy(RouteScoringProperties routeScoringProperties) {
        this.routeScoringProperties = routeScoringProperties;
    }

    @Override
    public boolean supports(RouteGoal routeGoal) {
        return true;
    }

    @Override
    public int score(CandidateRouteDTO route, RouteGenerationContext context) {
        int durationDelta = Math.abs(context.getGenerateParam().getDurationMinutes() - route.totalDurationMinutes());
        int score = this.config("base-score") - durationDelta;
        score += route.stops().size() * this.config("per-stop");
        if (RiskLevel.LOW == route.riskLevel()) {
            score += this.config("low-risk-bonus");
        }
        score += switch (context.getGenerateParam().getRouteGoal()) {
            case STEADY -> -route.totalDistanceMeters() / this.config("steady-distance-divisor");
            case QUIET -> RiskLevel.LOW == route.riskLevel() ? this.config("quiet-low-risk-bonus") : 0;
            case CLASSIC -> route.stops().size() * this.config("classic-per-stop");
            case LOCAL -> this.containsCategory(route, "LOCAL") || this.containsCategory(route, "FOOD")
                    ? this.config("local-hit-bonus")
                    : 0;
            case LOW_BUDGET -> route.budgetCent() == null
                    ? this.config("low-budget-missing-bonus")
                    : -route.budgetCent() / this.config("low-budget-divisor");
            case NIGHT -> this.containsCategory(route, "NIGHT") ? this.config("night-hit-bonus") : 0;
            case PHOTO -> this.containsCategory(route, "SCENIC") ? this.config("photo-hit-bonus") : 0;
        };
        return score;
    }

    private boolean containsCategory(CandidateRouteDTO route, String category) {
        return route.stops().stream().anyMatch(stop -> category.equals(stop.category()));
    }

    private int config(String path) {
        return this.routeScoringProperties.routeGoalScoringInt(path);
    }
}
