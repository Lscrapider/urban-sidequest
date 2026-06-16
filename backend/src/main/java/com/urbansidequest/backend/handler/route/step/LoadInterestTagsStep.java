package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.manage.InterestTagCatalogManage;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

@Component
public class LoadInterestTagsStep implements RouteGenerationStep {

    private final InterestTagCatalogManage interestTagCatalogManage;

    public LoadInterestTagsStep(InterestTagCatalogManage interestTagCatalogManage) {
        this.interestTagCatalogManage = interestTagCatalogManage;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setInterestTags(this.interestTagCatalogManage.findEnabledByTagCodes(context.getGenerateParam().getInterestTags()));
        if (!context.getGenerateParam().getInterestTags().isEmpty() && context.getInterestTags().isEmpty()) {
            context.addWarning("未找到已启用的兴趣标签映射，已使用默认候选点");
        }
    }
}
