package com.urbansidequest.backend.handler.route.segment;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import org.springframework.stereotype.Component;

@Component
public class TaxiCostStrategy extends AbstractHeuristicSegmentCostStrategy {

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
