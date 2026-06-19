package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.manage.UserPreferenceProfileManage;
import org.springframework.stereotype.Component;

/**
 * 加载用户问卷画像，作为后续 Linear Ranker 个性化 cross 的输入。
 */
@Component
public class LoadUserPreferenceProfileStep implements RouteGenerationStep {

    private final UserPreferenceProfileManage userPreferenceProfileManage;

    public LoadUserPreferenceProfileStep(UserPreferenceProfileManage userPreferenceProfileManage) {
        this.userPreferenceProfileManage = userPreferenceProfileManage;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        if (context.getGenerateParam().getUserPreferenceProfileOverride() != null) {
            context.setUserPreferenceProfile(context.getGenerateParam().getUserPreferenceProfileOverride());
            return;
        }
        context.setUserPreferenceProfile(this.userPreferenceProfileManage.findProfileByUserId(context.getUserId()));
    }
}
