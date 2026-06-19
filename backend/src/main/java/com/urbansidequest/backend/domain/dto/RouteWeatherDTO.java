package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.TemperatureLevel;
import com.urbansidequest.backend.domain.enums.WeatherBucket;
import com.urbansidequest.backend.domain.enums.WeatherLookupStatus;

public record RouteWeatherDTO(
        WeatherLookupStatus lookupStatus,
        WeatherBucket weatherBucket,
        TemperatureLevel temperatureLevel,
        String rawWeather,
        Integer temperatureCelsius,
        String windDirection,
        String rawWindPower,
        Integer windLevel,
        Integer humidity,
        Integer humidityLevel,
        String reportTime
) {

    public static RouteWeatherDTO unavailable() {
        return new RouteWeatherDTO(
                WeatherLookupStatus.UNAVAILABLE,
                WeatherBucket.UNKNOWN,
                TemperatureLevel.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RouteWeatherDTO failed() {
        return new RouteWeatherDTO(
                WeatherLookupStatus.FAILED,
                WeatherBucket.UNKNOWN,
                TemperatureLevel.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
