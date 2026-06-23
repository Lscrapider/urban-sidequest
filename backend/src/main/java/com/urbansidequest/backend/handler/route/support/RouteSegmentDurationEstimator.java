package com.urbansidequest.backend.handler.route.support;

import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import org.springframework.stereotype.Component;

@Component
public class RouteSegmentDurationEstimator {

    private final RouteScoringProperties routeScoringProperties;

    public RouteSegmentDurationEstimator(RouteScoringProperties routeScoringProperties) {
        this.routeScoringProperties = routeScoringProperties;
    }

    public int estimateDurationMinutes(int distanceMeters, SegmentTransportMode mode) {
        return switch (mode) {
            case BIKE -> this.estimate(distanceMeters, "bike", true);
            case BUS, SUBWAY, TRANSIT -> this.estimate(distanceMeters, "transit", true);
            case TAXI, DRIVE -> this.estimate(distanceMeters, "taxi", true);
            case WALK -> this.estimate(distanceMeters, "walk", false);
        };
    }

    private int estimate(int distanceMeters, String mode, boolean hasBase) {
        double speed = this.routeScoringProperties.estimateDurationDouble(mode + ".speed");
        int base = hasBase ? (int) this.routeScoringProperties.estimateDurationDouble(mode + ".base") : 0;
        return (int) Math.ceil(distanceMeters / speed) + base;
    }
}
