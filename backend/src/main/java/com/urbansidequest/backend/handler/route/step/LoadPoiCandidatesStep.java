package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.provider.route.PoiCandidateProvider;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

/**
 * 拉取路线生成使用的原始 POI 候选池。
 *
 * <p>具体数据源由 {@link PoiCandidateProvider} 决定，可以是高德真实 POI，
 * 也可以是本地兜底数据；本步骤只负责把候选池写回 context。</p>
 */
@Component
public class LoadPoiCandidatesStep implements RouteGenerationStep {

    private final PoiCandidateProvider poiCandidateProvider;

    public LoadPoiCandidatesStep(PoiCandidateProvider poiCandidateProvider) {
        this.poiCandidateProvider = poiCandidateProvider;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setPoiCandidates(this.poiCandidateProvider.loadCandidates(context));
    }
}
