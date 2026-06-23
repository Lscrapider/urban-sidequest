package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.constraint.ConstraintResult;
import com.urbansidequest.backend.handler.route.constraint.RouteConstraint;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 对候选路线执行后端硬约束复核和数量截断。
 *
 * <p>体验质量交给 Route X / judge 学习，当前 step 不再用粗规则打质量分；
 * 通过硬约束的路线保留 LLM 原始顺序，最多选出 5 条进入真实路径校准。</p>
 */
@Component
public class ScoreAndSelectRoutesStep implements RouteGenerationStep {

    private final List<RouteConstraint> routeConstraints;

    public ScoreAndSelectRoutesStep(
            List<RouteConstraint> routeConstraints
    ) {
        this.routeConstraints = routeConstraints;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getCandidateRoutes().isEmpty()) {
            context.setSelectedRoutes(List.of());
            return;
        }
        List<CandidateRouteDTO> selectedRoutes = context.getCandidateRoutes().stream()
                .filter(route -> this.passesConstraints(route, context))
                .limit(5)
                .toList();

        if (selectedRoutes.isEmpty()) {
            context.addWarning("没有候选路线通过后端约束，已返回空路线列表");
            context.setSelectedRoutes(List.of());
            return;
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

}
