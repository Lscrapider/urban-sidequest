package com.urbansidequest.backend.domain.enums;

import java.util.List;

public enum TransportProfile {
    WALK_ONLY(
            List.of(SegmentTransportMode.WALK),
            new DistanceBands(2000, 3500, 5000),
            new DistanceBands(1800, 3000, 4500),
            new DistanceBands(1500, 2500, 4000),
            new DistanceBands(1500, 2500, 4000)
    ),
    WALK_SUBWAY(
            List.of(SegmentTransportMode.SUBWAY),
            new DistanceBands(5000, 9000, 16000),
            new DistanceBands(3500, 6500, 11000),
            new DistanceBands(2500, 4000, 8000),
            new DistanceBands(4000, 6500, 11000)
    ),
    WALK_BUS(
            List.of(SegmentTransportMode.BUS),
            new DistanceBands(3500, 6000, 10000),
            new DistanceBands(2800, 4500, 7500),
            new DistanceBands(2200, 3500, 6500),
            new DistanceBands(3500, 5000, 8000)
    ),
    WALK_TRANSIT(
            List.of(SegmentTransportMode.TRANSIT),
            new DistanceBands(5000, 9000, 16000),
            new DistanceBands(3500, 6500, 11000),
            new DistanceBands(2500, 4000, 8000),
            new DistanceBands(4000, 6500, 11000)
    ),
    BIKE_SUBWAY(
            List.of(SegmentTransportMode.WALK, SegmentTransportMode.BIKE, SegmentTransportMode.TRANSIT),
            new DistanceBands(6000, 11000, 18000),
            new DistanceBands(4500, 8000, 13000),
            new DistanceBands(3000, 5000, 10000),
            new DistanceBands(5000, 8000, 13000)
    ),
    WALK_TAXI(
            List.of(SegmentTransportMode.WALK, SegmentTransportMode.TAXI),
            new DistanceBands(8000, 14000, 22000),
            new DistanceBands(5500, 9500, 15000),
            new DistanceBands(4000, 6000, 12000),
            new DistanceBands(8000, 12000, 18000)
    );

    private final List<SegmentTransportMode> allowedSegmentModes;

    private final DistanceBands searchRadiusMeters;

    private final DistanceBands poiDistanceRefMeters;

    private final DistanceBands routeDistanceRefMeters;

    private final DistanceBands walkingFatigueRefMeters;

    TransportProfile(
            List<SegmentTransportMode> allowedSegmentModes,
            DistanceBands searchRadiusMeters,
            DistanceBands poiDistanceRefMeters,
            DistanceBands routeDistanceRefMeters,
            DistanceBands walkingFatigueRefMeters
    ) {
        this.allowedSegmentModes = allowedSegmentModes;
        this.searchRadiusMeters = searchRadiusMeters;
        this.poiDistanceRefMeters = poiDistanceRefMeters;
        this.routeDistanceRefMeters = routeDistanceRefMeters;
        this.walkingFatigueRefMeters = walkingFatigueRefMeters;
    }

    public List<SegmentTransportMode> getAllowedSegmentModes() {
        return this.allowedSegmentModes;
    }

    public int searchRadiusMeters(DurationBucket durationBucket) {
        return this.searchRadiusMeters.value(durationBucket);
    }

    public int poiDistanceRefMeters(DurationBucket durationBucket) {
        return this.poiDistanceRefMeters.value(durationBucket);
    }

    public int routeDistanceRefMeters(DurationBucket durationBucket) {
        return this.routeDistanceRefMeters.value(durationBucket);
    }

    public int walkingFatigueRefMeters(DurationBucket durationBucket) {
        return this.walkingFatigueRefMeters.value(durationBucket);
    }

    private record DistanceBands(int shortMeters, int halfDayMeters, int fullDayMeters) {

        private int value(DurationBucket durationBucket) {
            return switch (durationBucket) {
                case SHORT -> this.shortMeters;
                case HALF_DAY -> this.halfDayMeters;
                case FULL_DAY -> this.fullDayMeters;
            };
        }
    }
}
