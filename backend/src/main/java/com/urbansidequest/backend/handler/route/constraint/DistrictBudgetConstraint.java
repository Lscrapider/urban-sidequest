package com.urbansidequest.backend.handler.route.constraint;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlan;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlanner;
import com.urbansidequest.backend.handler.route.support.RouteStopIdSupport;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 旧版片区预算硬约束。
 *
 * <p>主链路已退回“只挡不可展示路线”，跨片区压力不再作为硬过滤；
 * 后续如需片区相关 Route X 特征，可复用本类的片区统计方法。</p>
 */
@Deprecated
public class DistrictBudgetConstraint implements RouteConstraint {

    private final RouteDistrictPlanner routeDistrictPlanner;

    public DistrictBudgetConstraint(RouteDistrictPlanner routeDistrictPlanner) {
        this.routeDistrictPlanner = routeDistrictPlanner;
    }

    @Override
    public ConstraintResult check(CandidateRouteDTO route, RouteGenerationContext context) {
        int usedDistrictCount = this.usedDistrictCount(route, context);
        int budget = this.routeDistrictPlanner.plan(context).effectiveDistrictBudget();
        if (usedDistrictCount > budget) {
            return ConstraintResult.failed("跨片区数超过预算：" + usedDistrictCount + "/" + budget);
        }
        return ConstraintResult.success();
    }

    public int overBudgetCount(CandidateRouteDTO route, RouteGenerationContext context) {
        RouteDistrictPlan plan = this.routeDistrictPlanner.plan(context);
        return Math.max(0, this.usedDistrictCount(route, plan) - plan.effectiveDistrictBudget());
    }

    public int usedDistrictCount(CandidateRouteDTO route, RouteGenerationContext context) {
        return this.usedDistrictCount(route, this.routeDistrictPlanner.plan(context));
    }

    private int usedDistrictCount(CandidateRouteDTO route, RouteDistrictPlan plan) {
        Set<String> usedDistrictIds = new LinkedHashSet<>();
        for (RouteStopDTO stop : route.stops()) {
            String poiId = RouteStopIdSupport.poiIdFromStopId(stop.stopId(), route.routeCode());
            String districtId = plan.districtIdOf(poiId);
            if (districtId != null) {
                usedDistrictIds.add(districtId);
            }
        }
        return usedDistrictIds.size();
    }
}
