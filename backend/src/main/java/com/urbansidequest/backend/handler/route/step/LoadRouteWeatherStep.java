package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.provider.route.RouteWeatherProvider;
import org.springframework.stereotype.Component;

/**
 * 加载本次路线的环境天气原料。
 *
 * <p>天气仅作为 Linear Ranker 前的环境输入保存到 context，缺失或失败不阻断路线生成。</p>
 */
@Component
public class LoadRouteWeatherStep implements RouteGenerationStep {

    private final RouteWeatherProvider routeWeatherProvider;

    public LoadRouteWeatherStep(RouteWeatherProvider routeWeatherProvider) {
        this.routeWeatherProvider = routeWeatherProvider;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        context.setRouteWeather(this.routeWeatherProvider.loadWeather(context));
    }
}
