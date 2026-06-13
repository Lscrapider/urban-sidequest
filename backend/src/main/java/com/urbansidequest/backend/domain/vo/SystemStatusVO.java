package com.urbansidequest.backend.domain.vo;

import java.time.Instant;

public record SystemStatusVO(
        String service,
        String status,
        Instant checkedAt
) {
}

