package com.urbansidequest.backend.handler.route.segment;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import org.springframework.stereotype.Component;

@Component
public class WalkingCostStrategy extends AbstractHeuristicSegmentCostStrategy {

    // 已废弃：仅旧 Beam Search 路径使用，主 LLM 链路改走机密配置 estimate-duration（此处速度与其重复）。
    private static final int WALK_METERS_PER_MINUTE = 70;

    @Override
    public boolean supports(SegmentTransportMode mode) {
        return SegmentTransportMode.WALK == mode;
    }

    @Override
    protected SegmentTransportMode mode() {
        return SegmentTransportMode.WALK;
    }

    @Override
    protected int estimateDurationMinutes(int distanceMeters) {
        return (int) Math.ceil(distanceMeters / (double) WALK_METERS_PER_MINUTE);
    }

    @Override
    protected int walkDistanceMeters(int distanceMeters) {
        return distanceMeters;
    }

    @Override
    protected String summary(int distanceMeters, int durationMinutes) {
        return "步行约 " + Math.max(1, durationMinutes) + " 分钟";
    }
}
