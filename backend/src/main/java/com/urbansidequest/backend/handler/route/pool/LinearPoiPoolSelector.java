package com.urbansidequest.backend.handler.route.pool;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.handler.route.config.RouteScoringProperties;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.PoiLinearRanker;
import com.urbansidequest.backend.handler.route.pool.PoiDiversitySampler.RankedPoi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 用 Linear Ranker + 多样性采样预筛 POI 池：必去点强保留，其余在高分候选内受控随机。
 *
 * <p>v1 把每个候选的 8 个子分数 + linearScore 写入 context trace，并可通过 DEBUG 日志辅助调参。</p>
 */
@Primary
@Component
public class LinearPoiPoolSelector implements PoiPoolSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinearPoiPoolSelector.class);

    private final PoiLinearRanker linearRanker;

    private final PoiDiversitySampler poiDiversitySampler;

    private final RouteScoringProperties routeScoringProperties;

    public LinearPoiPoolSelector(
            PoiLinearRanker linearRanker,
            PoiDiversitySampler poiDiversitySampler,
            RouteScoringProperties routeScoringProperties
    ) {
        this.linearRanker = linearRanker;
        this.poiDiversitySampler = poiDiversitySampler;
        this.routeScoringProperties = routeScoringProperties;
    }

    @Override
    public List<PoiCandidateDTO> select(RouteGenerationContext context, List<PoiCandidateDTO> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<PoiLinearScoreDTO> scores = this.linearRanker.score(context, candidates);

        List<RankedPoi> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            scored.add(new RankedPoi(candidates.get(i), scores.get(i)));
        }
        // 先按 Linear 分数形成可解释排序，后续 sampler 在这个排序上做受控随机。
        scored.sort(Comparator
                .comparing((RankedPoi item) -> item.candidate().mustVisit()).reversed()
                .thenComparing(item -> item.score().linearScore(), Comparator.reverseOrder()));

        List<PoiLinearTraceDTO> traces = scored.stream()
                .map(item -> PoiLinearTraceDTO.from(
                        item.candidate(),
                        item.score(),
                        context.isTransportSignalAvailable()
                ))
                .toList();
        context.setPoiLinearTraces(traces);
        List<RankedPoi> selected = this.poiDiversitySampler.sample(
                context,
                scored,
                this.routeScoringProperties.diversitySamplerInt("max-llm-poi-count")
        );
        this.dumpScores(scored, selected, context.isTransportSignalAvailable());

        return selected.stream()
                .map(RankedPoi::candidate)
                .toList();
    }

    private void dumpScores(List<RankedPoi> scored, List<RankedPoi> selected, boolean transportSignalAvailable) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug(
                "Linear Ranker + Diversity Sampler 预筛 {} 个候选，选中 {} 个（transportSignalAvailable={}）：",
                scored.size(),
                selected.size(),
                transportSignalAvailable
        );
        for (RankedPoi item : scored) {
            PoiLinearScoreDTO s = item.score();
            LOGGER.debug(
                    "  [{}] poiId={} name={} linear={} | interest={} goal={} quality={} transport={} distance={} budget={} risk={} personalization={} | transitLookupStatus={} transportSignalAvailable={}",
                    item.candidate().mustVisit() ? "MUST" : "    ",
                    item.candidate().poiId(),
                    item.candidate().name(),
                    fmt(s.linearScore()),
                    fmt(s.interestScore()),
                    fmt(s.goalScore()),
                    fmt(s.qualityScore()),
                    fmt(s.transportScore()),
                    fmt(s.distanceCost()),
                    fmt(s.budgetCost()),
                    fmt(s.riskCost()),
                    fmt(s.personalizationScore()),
                    item.candidate().transitLookupStatus(),
                    transportSignalAvailable
            );
        }
    }

    private static String fmt(double value) {
        return String.format("%+.3f", value);
    }
}
