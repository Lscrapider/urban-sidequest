package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.handler.route.support.RouteAreaPolicy;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.stereotype.Component;

/**
 * 解析本次路线生成使用的地理范围。
 *
 * <p>根据用户选择的 areaMode，把自动半径、手动画框等输入统一转换为后续 POI
 * 搜索可直接使用的区域对象，并写入 context.area。</p>
 */
@Component
public class ResolveAreaStep implements RouteGenerationStep {

    private final RouteAreaPolicy routeAreaPolicy;

    public ResolveAreaStep(RouteAreaPolicy routeAreaPolicy) {
        this.routeAreaPolicy = routeAreaPolicy;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setArea(this.routeAreaPolicy.resolve(context.getGenerateParam()));
    }
}
