package com.urbansidequest.backend.domain.enums;

import java.util.List;

public enum TransportProfile {
    WALK_ONLY(
            List.of(SegmentTransportMode.WALK),
            1500,
            2500,
            4000
    ),
    WALK_SUBWAY(
            List.of(SegmentTransportMode.WALK, SegmentTransportMode.TRANSIT),
            2500,
            4000,
            8000
    ),
    BIKE_SUBWAY(
            List.of(SegmentTransportMode.WALK, SegmentTransportMode.BIKE, SegmentTransportMode.TRANSIT),
            3000,
            5000,
            10000
    ),
    WALK_TAXI(
            List.of(SegmentTransportMode.WALK, SegmentTransportMode.TAXI),
            4000,
            6000,
            12000
    );

    private final List<SegmentTransportMode> allowedSegmentModes;

    private final int shortRadiusMeters;

    private final int halfDayRadiusMeters;

    private final int fullDayRadiusMeters;

    TransportProfile(
            List<SegmentTransportMode> allowedSegmentModes,
            int shortRadiusMeters,
            int halfDayRadiusMeters,
            int fullDayRadiusMeters
    ) {
        this.allowedSegmentModes = allowedSegmentModes;
        this.shortRadiusMeters = shortRadiusMeters;
        this.halfDayRadiusMeters = halfDayRadiusMeters;
        this.fullDayRadiusMeters = fullDayRadiusMeters;
    }

    public List<SegmentTransportMode> getAllowedSegmentModes() {
        return this.allowedSegmentModes;
    }

    public int defaultRadiusMeters(DurationBucket durationBucket) {
        return switch (durationBucket) {
            case SHORT -> this.shortRadiusMeters;
            case HALF_DAY -> this.halfDayRadiusMeters;
            case FULL_DAY -> this.fullDayRadiusMeters;
        };
    }
}
