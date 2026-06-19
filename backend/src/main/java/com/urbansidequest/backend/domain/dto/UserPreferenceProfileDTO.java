package com.urbansidequest.backend.domain.dto;

import java.math.BigDecimal;
import java.util.Map;

public record UserPreferenceProfileDTO(
        BigDecimal distanceSensitivity,
        BigDecimal budgetSensitivity,
        BigDecimal transferSensitivity,
        BigDecimal hiddenGemAffinity,
        BigDecimal profileConfidence,
        String questionnaireVersion,
        boolean newUser,
        Map<String, BigDecimal> tagAffinities
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public UserPreferenceProfileDTO {
        distanceSensitivity = distanceSensitivity == null ? ZERO : distanceSensitivity;
        budgetSensitivity = budgetSensitivity == null ? ZERO : budgetSensitivity;
        transferSensitivity = transferSensitivity == null ? ZERO : transferSensitivity;
        hiddenGemAffinity = hiddenGemAffinity == null ? ZERO : hiddenGemAffinity;
        profileConfidence = profileConfidence == null ? ZERO : profileConfidence;
        tagAffinities = tagAffinities == null ? Map.of() : Map.copyOf(tagAffinities);
    }

    public static UserPreferenceProfileDTO empty() {
        return new UserPreferenceProfileDTO(
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                "v1",
                true,
                Map.of()
        );
    }
}
