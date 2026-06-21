package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.constraint.ConstraintResult;
import com.urbansidequest.backend.handler.route.constraint.RouteConstraint;
import com.urbansidequest.backend.handler.route.scoring.RouteGoalScoringStrategy;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 对候选路线执行后端约束复核、打分和最终筛选。
 *
 * <p>LLM 只负责给出路线草案，是否满足必去点、时长等硬约束仍由后端判断；
 * 通过约束的路线按当前目标策略打分，最多选出 5 条进入真实路径校准。</p>
 */
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
                .limit(5)
                .toList();

        if (selectedRoutes.isEmpty()) {
            throw new IllegalStateException("没有候选路线通过约束，已跳过本批路线偏好训练数据");
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
                route.segments(),
                score
        );
    }

}
