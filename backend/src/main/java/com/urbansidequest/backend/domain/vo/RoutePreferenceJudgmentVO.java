package com.urbansidequest.backend.domain.vo;

import java.util.UUID;

public record RoutePreferenceJudgmentVO(
        UUID judgmentId,
        UUID candidateSetId,
        String status
) {
}
