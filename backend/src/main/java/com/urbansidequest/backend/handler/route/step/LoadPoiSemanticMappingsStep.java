package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.manage.PoiSemanticMappingManage;
import org.springframework.stereotype.Component;

/**
 * 加载 POI 语义映射表，供后续 Linear Ranker extractor 使用。
 */
@Component
public class LoadPoiSemanticMappingsStep implements RouteGenerationStep {

    private final PoiSemanticMappingManage poiSemanticMappingManage;

    public LoadPoiSemanticMappingsStep(PoiSemanticMappingManage poiSemanticMappingManage) {
        this.poiSemanticMappingManage = poiSemanticMappingManage;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setPoiSemanticMappings(this.poiSemanticMappingManage.findEnabledMappings());
    }
}
