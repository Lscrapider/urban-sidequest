package com.urbansidequest.backend.domain.vo;

import java.time.Instant;

public record ErrorVO(
        Instant timestamp,
        int status,
        String error,
        String detail,
        String path
) {
}
