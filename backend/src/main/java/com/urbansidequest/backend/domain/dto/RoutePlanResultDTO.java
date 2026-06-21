package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.RoutePlanStatus;
import java.util.Optional;

public record RoutePlanResultDTO(
        RoutePlanStatus status,
        RoutePlanDTO plan
) {

    public static RoutePlanResultDTO success(RoutePlanDTO plan) {
        return new RoutePlanResultDTO(RoutePlanStatus.SUCCESS, plan);
    }

    public static RoutePlanResultDTO noRoute() {
        return new RoutePlanResultDTO(RoutePlanStatus.NO_ROUTE, null);
    }

    public static RoutePlanResultDTO unsupported() {
        return new RoutePlanResultDTO(RoutePlanStatus.UNSUPPORTED, null);
    }

    public static RoutePlanResultDTO temporaryFailure() {
        return new RoutePlanResultDTO(RoutePlanStatus.TEMPORARY_FAILURE, null);
    }

    public Optional<RoutePlanDTO> planOptional() {
        return Optional.ofNullable(this.plan);
    }
}
