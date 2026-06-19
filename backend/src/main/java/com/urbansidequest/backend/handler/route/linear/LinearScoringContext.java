package com.urbansidequest.backend.handler.route.linear;

import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteTimeStructure;
import com.urbansidequest.backend.domain.enums.TransportProfile;

/**
 * 一次请求级、对所有 POI 相同的 Linear 打分上下文（request/environment/userPreference 三组的派生量）。
 *
 * <p>这些都是"对同一请求所有候选取值相同"的量，不直接进 X，只作为 Delta selector / 归一基准 /
 * cross 原料。</p>
 */
public record LinearScoringContext(
        RouteGoal routeGoal,
        TransportProfile transportProfile,
        com.urbansidequest.backend.domain.enums.BudgetLevel budgetLevel,
        RouteTimeStructure routeTimeStructure,
        double effectiveRadiusMeters,
        double fatigueRefMeters,
        int routeStartMinutes,
        double badWeatherSeverity,
        double rainLevel,
        double heatLevel,
        boolean transportSignalAvailable,
        UserPreferenceProfileDTO profile,
        double affinityNorm
) {
}
