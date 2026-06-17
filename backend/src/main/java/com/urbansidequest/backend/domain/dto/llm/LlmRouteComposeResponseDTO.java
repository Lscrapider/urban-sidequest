package com.urbansidequest.backend.domain.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmRouteComposeResponseDTO(
        String overallVerdict,
        List<String> globalWarnings,
        List<LlmComposedRouteDTO> routes
) {
}
