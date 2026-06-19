package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.TransitLookupStatus;

/**
 * Linear Ranker 调试 trace：仅用于后端调参与排查，不进入正式响应模型。
 */
public record PoiLinearTraceDTO(
        String poiId,
        String name,
        TransitLookupStatus transitLookupStatus,
        boolean transportSignalAvailable,
        double interestScore,
        double goalScore,
        double qualityScore,
        double transportScore,
        double distanceCost,
        double budgetCost,
        double riskCost,
        double personalizationScore,
        double linearScore
) {

    public static PoiLinearTraceDTO from(
            PoiCandidateDTO candidate,
            PoiLinearScoreDTO score,
            boolean transportSignalAvailable
    ) {
        return new PoiLinearTraceDTO(
                candidate.poiId(),
                candidate.name(),
                candidate.transitLookupStatus(),
                transportSignalAvailable,
                score.interestScore(),
                score.goalScore(),
                score.qualityScore(),
                score.transportScore(),
                score.distanceCost(),
                score.budgetCost(),
                score.riskCost(),
                score.personalizationScore(),
                score.linearScore()
        );
    }
}
