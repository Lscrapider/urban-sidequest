package com.urbansidequest.backend.domain.dto;

public record TransitFacilityDTO(
        String type,
        String name,
        Integer distanceMeters
) {
}
