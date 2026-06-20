package com.urbansidequest.backend.domain.dto;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.domain.enums.HumidityLevel;
import com.urbansidequest.backend.domain.enums.TemperatureLevel;
import com.urbansidequest.backend.domain.enums.WeatherBucket;
import com.urbansidequest.backend.domain.enums.WeatherLookupStatus;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        HumidityLevel humidityLevel,
        String reportTime
) {

    private static final String STATUS_SUCCESS = "1";

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /**
     * 从高德实况天气响应解析路线天气；状态非成功或无实况数据时返回空，由调用方决定降级。
     *
     * <p>天气现象、温度、湿度的业务分级由各自枚举完成，本工厂只负责字段抽取和组装。
     */
    public static Optional<RouteWeatherDTO> fromAmapLive(JsonNode response) {
        if (response == null || !STATUS_SUCCESS.equals(response.path("status").asText())) {
            return Optional.empty();
        }
        JsonNode lives = response.path("lives");
        if (!lives.isArray() || lives.isEmpty()) {
            return Optional.empty();
        }
        JsonNode live = lives.get(0);
        String rawWeather = blankToNull(live.path("weather").asText());
        Integer temperatureCelsius = parseInteger(live.path("temperature").asText());
        String rawWindPower = blankToNull(live.path("windpower").asText());
        Integer humidity = parseInteger(live.path("humidity").asText());
        return Optional.of(new RouteWeatherDTO(
                WeatherLookupStatus.SUCCESS,
                WeatherBucket.fromRawWeather(rawWeather),
                TemperatureLevel.fromCelsius(temperatureCelsius),
                rawWeather,
                temperatureCelsius,
                blankToNull(live.path("winddirection").asText()),
                rawWindPower,
                parseMaxInteger(rawWindPower),
                humidity,
                HumidityLevel.fromHumidity(humidity),
                blankToNull(live.path("reporttime").asText())
        ));
    }

    public static RouteWeatherDTO unavailable() {
        return placeholder(WeatherLookupStatus.UNAVAILABLE);
    }

    public static RouteWeatherDTO failed() {
        return placeholder(WeatherLookupStatus.FAILED);
    }

    private static RouteWeatherDTO placeholder(WeatherLookupStatus lookupStatus) {
        return new RouteWeatherDTO(
                lookupStatus,
                WeatherBucket.UNKNOWN,
                TemperatureLevel.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                null,
                HumidityLevel.UNKNOWN,
                null
        );
    }

    private static Integer parseInteger(String value) {
        if (StrUtil.isBlank(value) || "[]".equals(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer parseMaxInteger(String value) {
        if (StrUtil.isBlank(value) || "[]".equals(value)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        Integer maxValue = null;
        while (matcher.find()) {
            Integer currentValue = Integer.valueOf(matcher.group());
            maxValue = maxValue == null ? currentValue : Math.max(maxValue, currentValue);
        }
        return maxValue;
    }

    private static String blankToNull(String value) {
        return StrUtil.isBlank(value) || "[]".equals(value) ? null : value;
    }
}
