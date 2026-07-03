package com.urbansidequest.backend.domain.vo;

import java.util.UUID;

public record RouteInteractionVO(
        UUID candidateSetId,
        String routeCode,
        boolean favorite,
        String reaction
) {
}
