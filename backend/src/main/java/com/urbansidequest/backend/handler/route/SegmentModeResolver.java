package com.urbansidequest.backend.handler.route;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.RouteStopDTO;
import com.urbansidequest.backend.domain.dto.TransitFacilityDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import com.urbansidequest.backend.handler.route.support.RouteStopIdSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SegmentModeResolver {

    private static final String TRANSIT_TYPE_BUS = "BUS";

    private static final String TRANSIT_TYPE_SUBWAY = "SUBWAY";

    private final RouteScoringProperties routeScoringProperties;

    public SegmentModeResolver(RouteScoringProperties routeScoringProperties) {
        this.routeScoringProperties = routeScoringProperties;
    }

    public SegmentTransportMode resolveInitialMode(
            TransportProfile profile,
            RouteStopDTO origin,
            RouteStopDTO destination,
            Map<String, PoiCandidateDTO> candidatesByPoiId,
            String routeCode
    ) {
        if (profile == null || origin.location() == null || destination.location() == null) {
            return SegmentTransportMode.WALK;
        }
        int distanceMeters = GeoMath.distanceMeters(origin.location(), destination.location());
        SegmentTransportMode mode = this.resolveByDistance(profile, distanceMeters);
        if (!this.requiresAccessGate(mode, distanceMeters)) {
            return mode;
        }
        PoiCandidateDTO originCandidate = candidatesByPoiId.get(RouteStopIdSupport.poiIdFromStopId(origin.stopId(), routeCode));
        PoiCandidateDTO destinationCandidate = candidatesByPoiId.get(RouteStopIdSupport.poiIdFromStopId(destination.stopId(), routeCode));
        if (this.skipAccessGate(originCandidate) || this.skipAccessGate(destinationCandidate)) {
            return mode;
        }
        if (this.hasTransitAccess(originCandidate, mode) && this.hasTransitAccess(destinationCandidate, mode)) {
            return mode;
        }
        return this.degradeByAccess(profile, mode);
    }

    public List<SegmentTransportMode> fallbackChain(SegmentTransportMode failedMode) {
        List<SegmentTransportMode> fallbackModes = switch (failedMode) {
            case SUBWAY, TRANSIT -> List.of(SegmentTransportMode.BUS, SegmentTransportMode.WALK);
            case BUS, BIKE, TAXI, DRIVE -> List.of(SegmentTransportMode.WALK);
            case WALK -> List.of();
        };
        List<SegmentTransportMode> uniqueModes = new ArrayList<>();
        for (SegmentTransportMode mode : fallbackModes) {
            if (mode != failedMode && !uniqueModes.contains(mode)) {
                uniqueModes.add(mode);
            }
        }
        return uniqueModes;
    }

    private SegmentTransportMode resolveByDistance(TransportProfile profile, int distanceMeters) {
        if (distanceMeters <= this.linearConstantInt("short-walk-segment-meters")) {
            return SegmentTransportMode.WALK;
        }
        return switch (profile) {
            case WALK_ONLY -> SegmentTransportMode.WALK;
            case WALK_SUBWAY -> distanceMeters <= this.linearConstantInt("subway-min-segment-meters")
                    ? SegmentTransportMode.WALK
                    : SegmentTransportMode.SUBWAY;
            case WALK_BUS -> SegmentTransportMode.BUS;
            case WALK_TRANSIT -> distanceMeters <= this.linearConstantInt("mid-distance-segment-meters")
                    ? SegmentTransportMode.BUS
                    : SegmentTransportMode.SUBWAY;
            case BIKE_SUBWAY -> distanceMeters <= this.linearConstantInt("mid-distance-segment-meters")
                    ? SegmentTransportMode.BIKE
                    : SegmentTransportMode.SUBWAY;
            case WALK_TAXI -> SegmentTransportMode.TAXI;
        };
    }

    private boolean requiresAccessGate(SegmentTransportMode mode, int distanceMeters) {
        return distanceMeters > this.linearConstantInt("short-walk-segment-meters")
                && distanceMeters <= this.linearConstantInt("mid-distance-segment-meters")
                && (mode == SegmentTransportMode.SUBWAY || mode == SegmentTransportMode.BUS);
    }

    private boolean skipAccessGate(PoiCandidateDTO candidate) {
        if (candidate == null) {
            return true;
        }
        return candidate.transitLookupStatus() == TransitLookupStatus.UNAVAILABLE
                || candidate.transitLookupStatus() == TransitLookupStatus.FAILED;
    }

    private boolean hasTransitAccess(PoiCandidateDTO candidate, SegmentTransportMode mode) {
        if (candidate == null) {
            return false;
        }
        if (candidate.transitLookupStatus() == TransitLookupStatus.SUCCESS_EMPTY) {
            return false;
        }
        String requiredType = this.requiredTransitType(mode);
        return candidate.nearestTransit().stream()
                .filter(transit -> requiredType.equals(transit.type()))
                .map(TransitFacilityDTO::distanceMeters)
                .filter(distanceMeters -> distanceMeters != null)
                .anyMatch(distanceMeters -> distanceMeters <= this.linearConstantInt("transit-access-max-meters"));
    }

    private String requiredTransitType(SegmentTransportMode mode) {
        return mode == SegmentTransportMode.SUBWAY ? TRANSIT_TYPE_SUBWAY : TRANSIT_TYPE_BUS;
    }

    private SegmentTransportMode degradeByAccess(TransportProfile profile, SegmentTransportMode mode) {
        if (mode == SegmentTransportMode.BUS) {
            return SegmentTransportMode.WALK;
        }
        return switch (profile) {
            case BIKE_SUBWAY -> SegmentTransportMode.BIKE;
            case WALK_TRANSIT, WALK_BUS -> SegmentTransportMode.BUS;
            case WALK_ONLY, WALK_SUBWAY, WALK_TAXI -> SegmentTransportMode.WALK;
        };
    }

    private int linearConstantInt(String path) {
        return this.routeScoringProperties.linearConstantInt(path);
    }
}
