package com.urbansidequest.backend.domain.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmComposedStopDTO(
        Integer order,
        String poiId,
        String routeRole,
        String intendedMealWindow,
        Integer stayMinutes,
        String description,
        String reason
) {
}
