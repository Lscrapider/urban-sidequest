package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.manage.InterestTagCatalogManage;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

/**
 * 加载用户兴趣标签对应的系统标签配置。
 *
 * <p>后续 POI 搜索计划和候选点标记都依赖这里解析出的兴趣标签；
 * 请求合法性已在 {@link ValidateRouteRequestStep} 前置校验。</p>
 */
@Component
public class LoadInterestTagsStep implements RouteGenerationStep {

    private final InterestTagCatalogManage interestTagCatalogManage;

    public LoadInterestTagsStep(InterestTagCatalogManage interestTagCatalogManage) {
        this.interestTagCatalogManage = interestTagCatalogManage;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setInterestTagCatalog(this.interestTagCatalogManage.findEnabled());
        context.setInterestTags(this.interestTagCatalogManage.findEnabledByTagCodes(context.getGenerateParam().getInterestTags()));
        if (!context.getGenerateParam().getInterestTags().isEmpty() && context.getInterestTags().isEmpty()) {
            context.addWarning("未找到已启用的兴趣标签映射，已使用默认候选点");
        }
    }
}
