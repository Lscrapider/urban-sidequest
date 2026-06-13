package com.urbansidequest.backend.handler.route.segment;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.service.route.GeoMath;
import com.urbansidequest.backend.service.route.RouteGenerationContext;

public abstract class AbstractHeuristicSegmentCostStrategy implements SegmentCostStrategy {

    @Override
    public SegmentCostDTO calculate(PoiCandidateDTO origin, PoiCandidateDTO destination, RouteGenerationContext context) {
        int distanceMeters = GeoMath.distanceMeters(origin.location(), destination.location());
        int durationMinutes = this.estimateDurationMinutes(distanceMeters);
        return new SegmentCostDTO(
                origin.poiId(),
                destination.poiId(),
                this.mode(),
                distanceMeters,
                Math.max(1, durationMinutes),
                this.walkDistanceMeters(distanceMeters),
                this.transferCount(distanceMeters),
                this.summary(distanceMeters, durationMinutes)
        );
    }

    protected abstract SegmentTransportMode mode();

    protected abstract int estimateDurationMinutes(int distanceMeters);

    protected int walkDistanceMeters(int distanceMeters) {
        return 0;
    }

    protected int transferCount(int distanceMeters) {
        return 0;
    }

    protected String summary(int distanceMeters, int durationMinutes) {
        return this.mode().name() + " " + Math.max(1, durationMinutes) + " 分钟";
    }
}
