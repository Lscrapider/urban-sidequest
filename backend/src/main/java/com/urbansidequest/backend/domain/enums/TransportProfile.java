package com.urbansidequest.backend.domain.enums;

import java.util.List;

public enum TransportProfile {
    WALK_ONLY(
            List.of(SegmentTransportMode.WALK)
    ),
    WALK_SUBWAY(
            List.of(SegmentTransportMode.SUBWAY)
    ),
    WALK_BUS(
            List.of(SegmentTransportMode.BUS)
    ),
    WALK_TRANSIT(
            List.of(SegmentTransportMode.TRANSIT)
    ),
    BIKE_SUBWAY(
            List.of(SegmentTransportMode.WALK, SegmentTransportMode.BIKE, SegmentTransportMode.TRANSIT)
    ),
    WALK_TAXI(
            List.of(SegmentTransportMode.WALK, SegmentTransportMode.TAXI)
    );

    private final List<SegmentTransportMode> allowedSegmentModes;

    TransportProfile(List<SegmentTransportMode> allowedSegmentModes) {
        this.allowedSegmentModes = allowedSegmentModes;
    }

    public List<SegmentTransportMode> getAllowedSegmentModes() {
        return this.allowedSegmentModes;
    }
}
