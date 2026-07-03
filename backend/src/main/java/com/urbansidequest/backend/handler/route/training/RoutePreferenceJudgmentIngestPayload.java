package com.urbansidequest.backend.handler.route.training;

import com.urbansidequest.backend.domain.enums.RoutePreferenceJudgmentStatus;
import com.urbansidequest.backend.domain.param.RoutePreferenceJudgmentParam;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoutePreferenceJudgmentIngestPayload(
        UUID judgmentId,
        UUID candidateSetId,
        String judgeType,
        String judgeModel,
        String judgePromptVersion,
        List<String> rankingJson,
        List<String> acceptedRouteCodesJson,
        List<String> rejectedRouteCodesJson,
        Map<String, List<String>> reasonCodesJson,
        BigDecimal confidence,
        String status,
        OffsetDateTime completedAt
) {

    public static RoutePreferenceJudgmentIngestPayload completed(UUID judgmentId, RoutePreferenceJudgmentParam param) {
        return new RoutePreferenceJudgmentIngestPayload(
                judgmentId,
                param.getCandidateSetId(),
                param.getJudgeType().name(),
                param.getJudgeModel(),
                param.getJudgePromptVersion(),
                List.copyOf(param.getRanking()),
                List.copyOf(param.getAcceptedRouteCodes()),
                List.copyOf(param.getRejectedRouteCodes()),
                Map.copyOf(param.getReasonCodes()),
                param.getConfidence(),
                RoutePreferenceJudgmentStatus.COMPLETED.name(),
                OffsetDateTime.now()
        );
    }
}
