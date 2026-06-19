package com.urbansidequest.backend.provider.route;

import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;

public interface RouteWeatherProvider {

    RouteWeatherDTO loadWeather(RouteGenerationContext context);
}
