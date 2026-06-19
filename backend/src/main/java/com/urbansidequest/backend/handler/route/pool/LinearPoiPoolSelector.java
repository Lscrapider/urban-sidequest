package com.urbansidequest.backend.handler.route.pool;

import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearScoreDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.PoiLinearRanker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 用 Linear Ranker 预筛 POI 池：必去点由 Gate 强保留置顶，其余按 linearScore 排序取前 N。
 *
 * <p>v1 把每个候选的 8 个子分数 + linearScore 写入 context trace，并可通过 DEBUG 日志辅助调参。</p>
 */
@Primary
@Component
public class LinearPoiPoolSelector implements PoiPoolSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinearPoiPoolSelector.class);

    private static final int MAX_LLM_POI_COUNT = 40;

    private final PoiLinearRanker linearRanker;

    public LinearPoiPoolSelector(PoiLinearRanker linearRanker) {
        this.linearRanker = linearRanker;
    }

    @Override
    public List<PoiCandidateDTO> select(RouteGenerationContext context, List<PoiCandidateDTO> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<PoiLinearScoreDTO> scores = this.linearRanker.score(context, candidates);

        List<ScoredCandidate> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            scored.add(new ScoredCandidate(candidates.get(i), scores.get(i)));
        }
        // 必去点（Gate 强保留）置顶，其余按 linearScore 降序。
        scored.sort(Comparator
                .comparing((ScoredCandidate item) -> item.candidate().mustVisit()).reversed()
                .thenComparing(item -> item.score().linearScore(), Comparator.reverseOrder()));

        List<PoiLinearTraceDTO> traces = scored.stream()
                .map(item -> PoiLinearTraceDTO.from(
                        item.candidate(),
                        item.score(),
                        context.isTransportSignalAvailable()
                ))
                .toList();
        context.setPoiLinearTraces(traces);
        this.dumpScores(scored, context.isTransportSignalAvailable());

        return scored.stream()
                .limit(MAX_LLM_POI_COUNT)
                .map(ScoredCandidate::candidate)
                .toList();
    }

    private void dumpScores(List<ScoredCandidate> scored, boolean transportSignalAvailable) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug(
                "Linear Ranker 预筛 {} 个候选（mustVisit 置顶，其余按 linearScore 降序，transportSignalAvailable={}）：",
                scored.size(),
                transportSignalAvailable
        );
        for (ScoredCandidate item : scored) {
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

    private record ScoredCandidate(PoiCandidateDTO candidate, PoiLinearScoreDTO score) {
    }
}
