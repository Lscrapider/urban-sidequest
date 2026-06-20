package com.urbansidequest.backend.domain.constant;

/**
 * 天气分级业务阈值。
 *
 * <p>这些取值是路线生成的业务契约，集中维护，供 {@code WeatherBucket}、{@code TemperatureLevel}、
 * {@code HumidityLevel} 的分级逻辑复用，禁止在解析或打分代码里散写 magic number。
 */
public final class WeatherClassificationConstant {

    /**
     * 低温阈值（摄氏度，含）：不高于该温度判为寒冷。
     */
    public static final int COLD_TEMPERATURE_CELSIUS = 5;

    /**
     * 高温阈值（摄氏度，含）：不低于该温度判为炎热。
     */
    public static final int HOT_TEMPERATURE_CELSIUS = 30;

    /**
     * 低湿度上界（百分比，不含）：低于该湿度判为干燥。
     */
    public static final int LOW_HUMIDITY_MAX = 40;

    /**
     * 高湿度下界（百分比，含）：不低于该湿度判为潮湿。
     */
    public static final int HIGH_HUMIDITY_MIN = 70;

    private WeatherClassificationConstant() {
    }
}
