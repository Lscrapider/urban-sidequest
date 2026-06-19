package com.urbansidequest.backend.provider.route;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.api.amap.AmapWeatherApi;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class AmapRouteWeatherProvider implements RouteWeatherProvider {

    private final AmapWeatherApi amapWeatherApi;

    public AmapRouteWeatherProvider(AmapWeatherApi amapWeatherApi) {
        this.amapWeatherApi = amapWeatherApi;
    }

    @Override
    public RouteWeatherDTO loadWeather(RouteGenerationContext context) {
        String routeCityAdcode = context.getGenerateParam().getRouteCityAdcode();
        if (!this.amapWeatherApi.isAvailable() || StrUtil.isBlank(routeCityAdcode)) {
            return RouteWeatherDTO.unavailable();
        }
        return this.amapWeatherApi.liveWeather(routeCityAdcode).orElseGet(RouteWeatherDTO::failed);
    }
}
