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
 * 通过约束的路线按当前目标策略打分，最多选出 3 条进入真实路径校准。</p>
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
                .limit(3)
                .toList();

        if (selectedRoutes.isEmpty()) {
            // 所有候选都没过约束时，仍返回耗时最短的草案，避免接口空结果。
            context.addWarning("没有路线完全满足约束，已返回最短超时路线");
            List<CandidateRouteDTO> overtimeRoutes = context.getCandidateRoutes().stream()
                    .sorted(Comparator.comparingInt(CandidateRouteDTO::totalDurationMinutes))
                    .limit(3)
                    .toList();
            selectedRoutes = new java.util.ArrayList<>();
            for (int index = 0; index < overtimeRoutes.size(); index++) {
                selectedRoutes.add(this.withRoutePresentation(overtimeRoutes.get(index), index));
            }
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

    private CandidateRouteDTO withRoutePresentation(CandidateRouteDTO route, int index) {
        String routeCode = switch (index) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
        };
        return new CandidateRouteDTO(
                routeCode,
                this.routeTitle(routeCode),
                route.summary(),
                route.totalDurationMinutes(),
                route.totalDistanceMeters(),
                route.budgetCent(),
                route.riskLevel(),
                route.explanation(),
                route.stops(),
                route.segments(),
                route.score()
        );
    }

    private String routeTitle(String routeCode) {
        return switch (routeCode) {
            case "A" -> "路线 A · 兴趣优先线";
            case "B" -> "路线 B · 节奏平衡线";
            default -> "路线 C · 轻量备选线";
        };
    }
}
