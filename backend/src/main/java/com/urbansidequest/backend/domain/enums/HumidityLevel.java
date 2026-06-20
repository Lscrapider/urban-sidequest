package com.urbansidequest.backend.domain.enums;

import com.urbansidequest.backend.domain.constant.WeatherClassificationConstant;

public enum HumidityLevel {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN;

    /**
     * 按湿度百分比分级；缺失时返回 {@link #UNKNOWN}，后续打分不据此扣分。
     */
    public static HumidityLevel fromHumidity(Integer humidity) {
        if (humidity == null) {
            return UNKNOWN;
        }
        if (humidity < WeatherClassificationConstant.LOW_HUMIDITY_MAX) {
            return LOW;
        }
        if (humidity >= WeatherClassificationConstant.HIGH_HUMIDITY_MIN) {
            return HIGH;
        }
        return MEDIUM;
    }
}
