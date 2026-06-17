package com.urbansidequest.backend.domain.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmBackendReviewHintDTO(
        String type,
        String message
) {
}
