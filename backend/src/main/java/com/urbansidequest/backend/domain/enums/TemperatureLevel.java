package com.urbansidequest.backend.domain.enums;

import com.urbansidequest.backend.domain.constant.WeatherClassificationConstant;

public enum TemperatureLevel {
    COLD,
    MILD,
    HOT,
    UNKNOWN;

    /**
     * 按摄氏温度分级；缺失时返回 {@link #UNKNOWN}，后续打分不据此扣分。
     */
    public static TemperatureLevel fromCelsius(Integer temperatureCelsius) {
        if (temperatureCelsius == null) {
            return UNKNOWN;
        }
        if (temperatureCelsius <= WeatherClassificationConstant.COLD_TEMPERATURE_CELSIUS) {
            return COLD;
        }
        if (temperatureCelsius >= WeatherClassificationConstant.HOT_TEMPERATURE_CELSIUS) {
            return HOT;
        }
        return MILD;
    }
}
