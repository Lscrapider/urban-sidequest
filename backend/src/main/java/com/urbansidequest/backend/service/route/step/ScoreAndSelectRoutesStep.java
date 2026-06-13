package com.urbansidequest.backend.service.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.constraint.ConstraintResult;
import com.urbansidequest.backend.handler.route.constraint.RouteConstraint;
import com.urbansidequest.backend.handler.route.scoring.RouteGoalScoringStrategy;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(80)
@Component
public class ScoreAndSelectRoutesStep implements RouteGenerationStep {

    private final List<RouteConstraint> routeConstraints;

    private final List<RouteGoalScoringStrategy> scoringStrategies;

    public ScoreAndSelectRoutesStep(
            List<RouteConstraint> routeConstraints,
            List<RouteGoalScoringStrategy> scoringStrategies
    ) {
        this.routeConstraints = routeConstraints;
        this.scoringStrategies = scoringStrategies;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        RouteGoalScoringStrategy scoringStrategy = this.scoringStrategies.stream()
                .filter(strategy -> strategy.supports(context.getGenerateParam().getRouteGoal()))
                .findFirst()
                .orElseThrow();

        List<CandidateRouteDTO> selectedRoutes = context.getCandidateRoutes().stream()
                .filter(route -> this.passesConstraints(route, context))
                .map(route -> this.withScore(route, scoringStrategy.score(route, context)))
                .sorted(Comparator.comparingInt(CandidateRouteDTO::score).reversed())
                .limit(3)
                .toList();

        if (selectedRoutes.isEmpty()) {
            context.addWarning("没有路线完全满足约束，已返回约束前候选路线");
            selectedRoutes = context.getCandidateRoutes().stream().limit(3).toList();
        }
        context.setSelectedRoutes(selectedRoutes);
    }

    private boolean passesConstraints(CandidateRouteDTO route, RouteGenerationContext context) {
        for (RouteConstraint constraint : this.routeConstraints) {
            ConstraintResult result = constraint.check(route, context);
            if (!result.passed()) {
                context.addWarning(route.routeCode() + " 未通过约束：" + result.reason());
                return false;
            }
        }
        return true;
    }

    private CandidateRouteDTO withScore(CandidateRouteDTO route, int score) {
        return new CandidateRouteDTO(
                route.routeCode(),
                route.title(),
                route.summary(),
                route.totalDurationMinutes(),
                route.totalDistanceMeters(),
                route.budgetCent(),
                route.riskLevel(),
                route.explanation(),
                route.stops(),
                score
        );
    }
}
