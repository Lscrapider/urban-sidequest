package com.urbansidequest.backend.domain.enums;

import java.time.Instant;
import java.time.ZoneId;

/**
 * 路线时段结构，由路线时间窗（departureTime + durationMinutes）派生，不是当前系统时间。
 *
 * <p>它是 Linear Ranker 允许驱动 Delta 的小枚举之一（仅 NIGHT 做非语义位移）；
 * 语义时段匹配（夜游/饭点）走 nightMatch/mealMatch cross 值，不靠本枚举调权重。</p>
 */
public enum RouteTimeStructure {
    MORNING,
    LUNCH,
    AFTERNOON,
    DINNER,
    NIGHT;

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 由路线开始时刻落档。v1 用开始小时落桶，缺省给中性的 AFTERNOON。
     */
    public static RouteTimeStructure fromWindow(Instant departureTime, Integer durationMinutes) {
        if (departureTime == null) {
            return AFTERNOON;
        }
        int hour = departureTime.atZone(CHINA_ZONE).getHour();
        if (hour >= 19 || hour < 6) {
            return NIGHT;
        }
        if (hour >= 17) {
            return DINNER;
        }
        if (hour >= 14) {
            return AFTERNOON;
        }
        if (hour >= 11) {
            return LUNCH;
        }
        return MORNING;
    }

    public boolean isNight() {
        return this == NIGHT;
    }

    public boolean isMealWindow() {
        return this == LUNCH || this == DINNER;
    }
}
