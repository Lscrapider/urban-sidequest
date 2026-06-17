package com.urbansidequest.backend.handler.route.step;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import com.urbansidequest.backend.handler.route.segment.SegmentCostStrategy;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 构建 POI 之间的本地估算路段成本图。
 *
 * <p>这是旧 Beam Search 流程使用的步骤：为每个候选点连接附近若干个候选点，
 * 并按允许的交通方式生成估算成本。当前 LLM 流水线不直接使用它，但保留给旧算法和回退方案。</p>
 */
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
            // 只连接附近点，避免候选池较大时生成完整 N*N 成本图。
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
