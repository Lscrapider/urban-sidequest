package com.urbansidequest.backend.handler.route.segment;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import org.springframework.stereotype.Component;

@Component
public class TaxiCostStrategy extends AbstractHeuristicSegmentCostStrategy {

    // 已废弃：仅旧 Beam Search 路径使用，主 LLM 链路改走机密配置 estimate-duration（此处速度与其重复）。
    private static final int TAXI_METERS_PER_MINUTE = 300;

    @Override
    public boolean supports(SegmentTransportMode mode) {
        return SegmentTransportMode.TAXI == mode || SegmentTransportMode.DRIVE == mode;
    }

    @Override
    protected SegmentTransportMode mode() {
        return SegmentTransportMode.TAXI;
    }

    @Override
    protected int estimateDurationMinutes(int distanceMeters) {
        return (int) Math.ceil(distanceMeters / (double) TAXI_METERS_PER_MINUTE) + 8;
    }

    @Override
    protected String summary(int distanceMeters, int durationMinutes) {
        return "打车约 " + Math.max(1, durationMinutes) + " 分钟";
    }
}
