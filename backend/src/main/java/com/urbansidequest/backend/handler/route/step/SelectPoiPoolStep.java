package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.pool.PoiPoolSelector;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 将较大的 POI 候选池筛成适合路线编排的小池。
 *
 * <p>这个步骤是 LLM 编排前的质量入口：未来会在 {@link PoiPoolSelector}
 * 中综合兴趣匹配、评分、餐饮/休息保底、空间分布和交通可达性完成筛选。</p>
 */
@Component
public class SelectPoiPoolStep implements RouteGenerationStep {

    private final PoiPoolSelector poiPoolSelector;

    public SelectPoiPoolStep(PoiPoolSelector poiPoolSelector) {
        this.poiPoolSelector = poiPoolSelector;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        List<PoiCandidateDTO> selectedCandidates = this.poiPoolSelector.select(context, context.getPoiCandidates());
        context.setPoiCandidates(selectedCandidates);
        if (selectedCandidates.isEmpty()) {
            context.addWarning("POI 池筛选后没有可用候选点");
        }
    }
}
