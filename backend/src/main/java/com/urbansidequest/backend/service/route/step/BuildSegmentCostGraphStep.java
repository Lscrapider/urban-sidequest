package com.urbansidequest.backend.service.route.step;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.handler.route.segment.SegmentCostStrategy;
import com.urbansidequest.backend.service.route.GeoMath;
import com.urbansidequest.backend.service.route.RouteGenerationContext;
import com.urbansidequest.backend.service.route.RouteGenerationStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(60)
@Component
public class BuildSegmentCostGraphStep implements RouteGenerationStep {

    private static final int NEAREST_NEIGHBOR_LIMIT = 8;

    private final List<SegmentCostStrategy> segmentCostStrategies;

    public BuildSegmentCostGraphStep(List<SegmentCostStrategy> segmentCostStrategies) {
        this.segmentCostStrategies = segmentCostStrategies;
    }

    @Override
    public void execute(RouteGenerationContext context) {
        List<SegmentCostDTO> segmentCosts = new ArrayList<>();
        List<PoiCandidateDTO> candidates = context.getPoiCandidates();
        for (PoiCandidateDTO origin : candidates) {
            List<PoiCandidateDTO> nearestCandidates = candidates.stream()
                    .filter(candidate -> !candidate.poiId().equals(origin.poiId()))
                    .sorted(Comparator.comparingInt(candidate -> GeoMath.distanceMeters(origin.location(), candidate.location())))
                    .limit(NEAREST_NEIGHBOR_LIMIT)
                    .toList();
            for (PoiCandidateDTO destination : nearestCandidates) {
                segmentCosts.addAll(this.calculateAllowedModeCosts(origin, destination, context));
            }
        }
        context.setSegmentCosts(segmentCosts);
    }

    private List<SegmentCostDTO> calculateAllowedModeCosts(
            PoiCandidateDTO origin,
            PoiCandidateDTO destination,
            RouteGenerationContext context
    ) {
        List<SegmentCostDTO> costs = new ArrayList<>();
        for (SegmentTransportMode mode : context.getGenerateParam().getTransportProfile().getAllowedSegmentModes()) {
            this.segmentCostStrategies.stream()
                    .filter(strategy -> strategy.supports(mode))
                    .findFirst()
                    .map(strategy -> strategy.calculate(origin, destination, context))
                    .ifPresent(costs::add);
        }
        return costs;
    }
}
