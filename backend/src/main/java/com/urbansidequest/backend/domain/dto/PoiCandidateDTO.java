package com.urbansidequest.backend.domain.dto;

import com.urbansidequest.backend.domain.enums.PoiCandidateRole;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import java.math.BigDecimal;
import java.util.List;

public record PoiCandidateDTO(
        String poiId,
        String amapPoiId,
        String name,
        String category,
        PoiCandidateRole role,
        GeoPointDTO location,
        String address,
        String description,
        BigDecimal amapRating,
        Integer avgPriceCent,
        List<String> matchedInterestTags,
        List<String> imageUrls,
        String rawType,
        String typecode,
        String opentimeToday,
        String opentimeWeek,
        String keytag,
        String rectag,
        Integer amapDistanceMeters,
        List<TransitFacilityDTO> nearestTransit,
        String transitAccessibility,
        TransitLookupStatus transitLookupStatus,
        boolean mustVisit,
        String reasonSeed
) {

    public PoiCandidateDTO {
        matchedInterestTags = matchedInterestTags == null ? List.of() : List.copyOf(matchedInterestTags);
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        nearestTransit = nearestTransit == null ? List.of() : List.copyOf(nearestTransit);
        transitLookupStatus = transitLookupStatus == null ? TransitLookupStatus.UNAVAILABLE : transitLookupStatus;
    }

    public PoiCandidateDTO(
            String poiId,
            String amapPoiId,
            String name,
            String category,
            PoiCandidateRole role,
            GeoPointDTO location,
            String address,
            String description,
            BigDecimal amapRating,
            Integer avgPriceCent,
            List<String> matchedInterestTags,
            List<String> imageUrls,
            List<TransitFacilityDTO> nearestTransit,
            String transitAccessibility,
            boolean mustVisit,
            String reasonSeed
    ) {
        this(
                poiId,
                amapPoiId,
                name,
                category,
                role,
                location,
                address,
                description,
                amapRating,
                avgPriceCent,
                matchedInterestTags,
                imageUrls,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                nearestTransit,
                transitAccessibility,
                TransitLookupStatus.UNAVAILABLE,
                mustVisit,
                reasonSeed
        );
    }

    public PoiCandidateDTO withTransitDetails(
            List<TransitFacilityDTO> nearestTransit,
            String transitAccessibility,
            TransitLookupStatus transitLookupStatus
    ) {
        return new PoiCandidateDTO(
                this.poiId,
                this.amapPoiId,
                this.name,
                this.category,
                this.role,
                this.location,
                this.address,
                this.description,
                this.amapRating,
                this.avgPriceCent,
                this.matchedInterestTags,
                this.imageUrls,
                this.rawType,
                this.typecode,
                this.opentimeToday,
                this.opentimeWeek,
                this.keytag,
                this.rectag,
                this.amapDistanceMeters,
                nearestTransit,
                transitAccessibility,
                transitLookupStatus,
                this.mustVisit,
                this.reasonSeed
        );
    }
}
