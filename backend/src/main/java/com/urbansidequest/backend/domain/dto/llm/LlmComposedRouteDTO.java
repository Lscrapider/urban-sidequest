package com.urbansidequest.backend.domain.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmComposedRouteDTO(
        String routeCode,
        String title,
        String theme,
        String summary,
        String explanation,
        Integer estimatedStayMinutes,
        Integer qualityScore,
        List<String> routeTags,
        List<LlmComposedStopDTO> stops,
        List<String> warnings,
        List<LlmBackendReviewHintDTO> backendReviewHints,
        Boolean needsBackendReview
) {
}
