package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.search.RouteCandidateComposer;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 基于筛选后的 POI 池生成候选路线草案。
 *
 * <p>当前实现通过 {@link RouteCandidateComposer} 调用大模型做路线编排；
 * 真实路段耗时、距离和 polyline 不在本步骤计算，后续由校准步骤处理。</p>
 */
@Component
public class BuildCandidateRoutesStep implements RouteGenerationStep {

    private final RouteCandidateComposer routeCandidateComposer;

    public BuildCandidateRoutesStep(RouteCandidateComposer routeCandidateComposer) {
        this.routeCandidateComposer = routeCandidateComposer;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getPoiCandidates().isEmpty()) {
            context.setCandidateRoutes(List.of());
            context.addWarning("当前范围内没有可用候选点");
            return;
        }
        List<CandidateRouteDTO> routes = this.routeCandidateComposer.composeRoutes(context);
        if (routes.isEmpty()) {
            context.addWarning("大模型没有生成可用候选路线");
        }
        context.setCandidateRoutes(routes);
    }
}
