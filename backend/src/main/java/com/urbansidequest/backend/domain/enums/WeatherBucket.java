package com.urbansidequest.backend.domain.enums;

import cn.hutool.core.util.StrUtil;

public enum WeatherBucket {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    EXTREME,
    OTHER,
    UNKNOWN;

    /**
     * 把高德返回的中文天气现象归并到业务分桶；空值返回 {@link #UNKNOWN}，无法识别返回 {@link #OTHER}。
     */
    public static WeatherBucket fromRawWeather(String rawWeather) {
        if (StrUtil.isBlank(rawWeather)) {
            return UNKNOWN;
        }
        if (rawWeather.contains("雪")) {
            return SNOW;
        }
        if (rawWeather.contains("暴") || rawWeather.contains("雷") || rawWeather.contains("冰雹")
                || rawWeather.contains("沙") || rawWeather.contains("尘") || rawWeather.contains("霾")
                || rawWeather.contains("雾")) {
            return EXTREME;
        }
        if (rawWeather.contains("雨")) {
            return RAIN;
        }
        if (rawWeather.contains("晴")) {
            return CLEAR;
        }
        if (rawWeather.contains("云") || rawWeather.contains("阴")) {
            return CLOUDY;
        }
        return OTHER;
    }
}
