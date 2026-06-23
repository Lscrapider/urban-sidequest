package com.urbansidequest.backend.handler.route.constraint;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;

/**
 * 旧版校准前时长硬约束。
 *
 * <p>主链路不再使用 LLM 自报总时长做硬过滤；时间硬超限已迁移到
 * 校准后的 {@code FilterCalibratedRoutesStep}，避免误杀真实可用路线。</p>
 */
@Deprecated
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
