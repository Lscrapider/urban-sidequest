package com.urbansidequest.backend.handler.route.segment;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import org.springframework.stereotype.Component;

@Component
public class TransitCostStrategy extends AbstractHeuristicSegmentCostStrategy {

    private static final int TRANSIT_METERS_PER_MINUTE = 260;

    @Override
    public boolean supports(SegmentTransportMode mode) {
        return SegmentTransportMode.TRANSIT == mode || SegmentTransportMode.SUBWAY == mode || SegmentTransportMode.BUS == mode;
    }

    @Override
    protected SegmentTransportMode mode() {
        return SegmentTransportMode.TRANSIT;
    }

    @Override
    protected int estimateDurationMinutes(int distanceMeters) {
        return (int) Math.ceil(distanceMeters / (double) TRANSIT_METERS_PER_MINUTE) + 12;
    }

    @Override
    protected int walkDistanceMeters(int distanceMeters) {
        return Math.min(900, Math.max(300, distanceMeters / 8));
    }

    @Override
    protected int transferCount(int distanceMeters) {
        return distanceMeters > 5000 ? 1 : 0;
    }

    @Override
    protected String summary(int distanceMeters, int durationMinutes) {
        return "公共交通约 " + Math.max(1, durationMinutes) + " 分钟";
    }
}
