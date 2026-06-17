package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.manage.InterestTagCatalogManage;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

/**
 * 加载用户兴趣标签对应的系统标签配置。
 *
 * <p>后续 POI 搜索计划和候选点标记都依赖这里解析出的兴趣标签；如果用户传入了
 * 兴趣但没有命中启用配置，则保留流程继续执行并追加 warning。</p>
 */
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
