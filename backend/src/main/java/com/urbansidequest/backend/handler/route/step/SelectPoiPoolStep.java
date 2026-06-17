package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.pool.PoiPoolSelector;
import java.util.List;
import org.springframework.stereotype.Component;

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
