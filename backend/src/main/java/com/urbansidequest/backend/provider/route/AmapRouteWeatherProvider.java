package com.urbansidequest.backend.provider.route;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.api.amap.AmapApi;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class AmapRouteWeatherProvider implements RouteWeatherProvider {

    private final AmapApi amapApi;

    public AmapRouteWeatherProvider(AmapApi amapApi) {
        this.amapApi = amapApi;
    }

    @Override
    public RouteWeatherDTO loadWeather(RouteGenerationContext context) {
        String routeCityAdcode = context.getGenerateParam().getRouteCityAdcode();
        if (!this.amapApi.isAvailable() || StrUtil.isBlank(routeCityAdcode)) {
            return RouteWeatherDTO.unavailable();
        }
        return this.amapApi.liveWeather(routeCityAdcode).orElseGet(RouteWeatherDTO::failed);
    }
}
