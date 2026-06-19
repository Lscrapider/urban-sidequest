package com.urbansidequest.backend.api.amap;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.config.AmapWebProperties;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.enums.TemperatureLevel;
import com.urbansidequest.backend.domain.enums.WeatherBucket;
import com.urbansidequest.backend.domain.enums.WeatherLookupStatus;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AmapWeatherApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmapWeatherApi.class);

    private static final String STATUS_SUCCESS = "1";

    private static final String EXTENSIONS_BASE = "base";

    private static final int COLD_TEMPERATURE_CELSIUS = 5;

    private static final int HOT_TEMPERATURE_CELSIUS = 30;

    private static final int LOW_HUMIDITY_MAX = 40;

    private static final int HIGH_HUMIDITY_MIN = 70;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final AmapWebProperties amapWebProperties;

    private final RestTemplate restTemplate;

    public AmapWeatherApi(AmapWebProperties amapWebProperties, RestTemplateBuilder restTemplateBuilder) {
        this.amapWebProperties = amapWebProperties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(amapWebProperties.getConnectTimeout())
                .readTimeout(amapWebProperties.getReadTimeout())
                .build();
    }

    public Optional<RouteWeatherDTO> liveWeather(String cityAdcode) {
        if (!this.isAvailable() || StrUtil.isBlank(cityAdcode)) {
            return Optional.empty();
        }
        try {
            JsonNode response = this.restTemplate.getForObject(this.buildUri(cityAdcode), JsonNode.class);
            return this.parseLiveWeather(response);
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("高德天气查询失败，cityAdcode={}", cityAdcode, exception);
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return StrUtil.isNotBlank(this.amapWebProperties.getKey());
    }

    private URI buildUri(String cityAdcode) {
        return UriComponentsBuilder.fromUriString(this.amapWebProperties.getBaseUrl())
                .path("/v3/weather/weatherInfo")
                .queryParam("key", this.amapWebProperties.getKey())
                .queryParam("city", cityAdcode)
                .queryParam("extensions", EXTENSIONS_BASE)
                .build()
                .toUri();
    }

    private Optional<RouteWeatherDTO> parseLiveWeather(JsonNode response) {
        if (response == null || !STATUS_SUCCESS.equals(response.path("status").asText())) {
            this.logUnsuccessfulResponse(response);
            return Optional.empty();
        }
        JsonNode lives = response.path("lives");
        if (!lives.isArray() || lives.isEmpty()) {
            return Optional.empty();
        }
        JsonNode live = lives.get(0);
        String rawWeather = this.blankToNull(live.path("weather").asText());
        Integer temperature = this.parseInteger(live.path("temperature").asText());
        String rawWindPower = this.blankToNull(live.path("windpower").asText());
        Integer humidity = this.parseInteger(live.path("humidity").asText());
        return Optional.of(new RouteWeatherDTO(
                WeatherLookupStatus.SUCCESS,
                this.weatherBucket(rawWeather),
                this.temperatureLevel(temperature),
                rawWeather,
                temperature,
                this.blankToNull(live.path("winddirection").asText()),
                rawWindPower,
                this.parseMaxInteger(rawWindPower),
                humidity,
                this.humidityLevel(humidity),
                this.blankToNull(live.path("reporttime").asText())
        ));
    }

    private void logUnsuccessfulResponse(JsonNode response) {
        if (response == null) {
            LOGGER.warn("高德天气返回空响应");
            return;
        }
        LOGGER.warn(
                "高德天气返回失败，status={}，info={}，infocode={}",
                response.path("status").asText(""),
                response.path("info").asText(""),
                response.path("infocode").asText("")
        );
    }

    private WeatherBucket weatherBucket(String rawWeather) {
        if (StrUtil.isBlank(rawWeather)) {
            return WeatherBucket.UNKNOWN;
        }
        if (rawWeather.contains("雪")) {
            return WeatherBucket.SNOW;
        }
        if (rawWeather.contains("暴") || rawWeather.contains("雷") || rawWeather.contains("冰雹")
                || rawWeather.contains("沙") || rawWeather.contains("尘") || rawWeather.contains("霾")
                || rawWeather.contains("雾")) {
            return WeatherBucket.EXTREME;
        }
        if (rawWeather.contains("雨")) {
            return WeatherBucket.RAIN;
        }
        if (rawWeather.contains("晴")) {
            return WeatherBucket.CLEAR;
        }
        if (rawWeather.contains("云") || rawWeather.contains("阴")) {
            return WeatherBucket.CLOUDY;
        }
        return WeatherBucket.OTHER;
    }

    private TemperatureLevel temperatureLevel(Integer temperature) {
        if (temperature == null) {
            return TemperatureLevel.UNKNOWN;
        }
        if (temperature <= COLD_TEMPERATURE_CELSIUS) {
            return TemperatureLevel.COLD;
        }
        if (temperature >= HOT_TEMPERATURE_CELSIUS) {
            return TemperatureLevel.HOT;
        }
        return TemperatureLevel.MILD;
    }

    private Integer humidityLevel(Integer humidity) {
        if (humidity == null) {
            return null;
        }
        if (humidity < LOW_HUMIDITY_MAX) {
            return 0;
        }
        if (humidity >= HIGH_HUMIDITY_MIN) {
            return 2;
        }
        return 1;
    }

    private Integer parseInteger(String value) {
        if (StrUtil.isBlank(value) || "[]".equals(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseMaxInteger(String value) {
        if (StrUtil.isBlank(value) || "[]".equals(value)) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        Integer maxValue = null;
        while (matcher.find()) {
            int currentValue = Integer.parseInt(matcher.group());
            maxValue = maxValue == null ? currentValue : Math.max(maxValue, currentValue);
        }
        return maxValue;
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) || "[]".equals(value) ? null : value;
    }
}
