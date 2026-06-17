package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.provider.route.PoiDetailProvider;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

/**
 * 增强 POI 候选点的详情和辅助决策信息。
 *
 * <p>当前主要用于批量补充交通可达性等结构化字段。这里不逐个 POI 调详情接口，
 * 具体批量策略由 {@link PoiDetailProvider} 实现。</p>
 */
@Component
public class EnrichPoiDetailsStep implements RouteGenerationStep {

    private final PoiDetailProvider poiDetailProvider;

    public EnrichPoiDetailsStep(PoiDetailProvider poiDetailProvider) {
        this.poiDetailProvider = poiDetailProvider;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setPoiCandidates(this.poiDetailProvider.enrichDetails(context, context.getPoiCandidates()));
    }
}
