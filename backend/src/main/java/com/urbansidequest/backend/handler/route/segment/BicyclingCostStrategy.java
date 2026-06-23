package com.urbansidequest.backend.handler.route.segment;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import org.springframework.stereotype.Component;

@Component
public class BicyclingCostStrategy extends AbstractHeuristicSegmentCostStrategy {

    // 已废弃：仅旧 Beam Search 路径使用，主 LLM 链路改走机密配置 estimate-duration（此处速度与其重复）。
    private static final int BIKE_METERS_PER_MINUTE = 180;

    @Override
    public boolean supports(SegmentTransportMode mode) {
        return SegmentTransportMode.BIKE == mode;
    }

    @Override
    protected SegmentTransportMode mode() {
        return SegmentTransportMode.BIKE;
    }

    @Override
    protected int estimateDurationMinutes(int distanceMeters) {
        return (int) Math.ceil(distanceMeters / (double) BIKE_METERS_PER_MINUTE) + 3;
    }

    @Override
    protected String summary(int distanceMeters, int durationMinutes) {
        return "骑行约 " + Math.max(1, durationMinutes) + " 分钟";
    }
}
