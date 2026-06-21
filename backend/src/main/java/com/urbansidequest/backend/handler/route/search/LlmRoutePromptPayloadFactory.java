package com.urbansidequest.backend.handler.route.search;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.TransitFacilityDTO;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.district.RouteDistrict;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlan;
import com.urbansidequest.backend.handler.route.district.RouteDistrictPlanner;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LlmRoutePromptPayloadFactory {

    private static final String TRANSIT_TYPE_BUS = "BUS";

    private static final String TRANSIT_TYPE_SUBWAY = "SUBWAY";

    private static final int MAX_ROUTE_COUNT = 5;

    private final RouteDistrictPlanner routeDistrictPlanner;

    public LlmRoutePromptPayloadFactory(RouteDistrictPlanner routeDistrictPlanner) {
        this.routeDistrictPlanner = routeDistrictPlanner;
    }

    Map<String, Object> toPromptPayload(RouteGenerationContext context) {
        RouteDistrictPlan districtPlan = this.routeDistrictPlanner.plan(context);
        Map<String, PoiCandidateDTO> candidatesById = new LinkedHashMap<>();
        for (PoiCandidateDTO candidate : context.getPoiCandidates()) {
            candidatesById.put(candidate.poiId(), candidate);
        }

        return Map.of(
                "request", this.requestPayload(context),
                "mealWindows", List.of(
                        Map.of("type", "LUNCH", "start", "11:30", "end", "13:30"),
                        Map.of("type", "DINNER", "start", "17:30", "end", "20:00")
                ),
                "transportPolicy", Map.of(
                        "profile", context.getGenerateParam().getTransportProfile(),
                        "districtBudget", districtPlan.effectiveDistrictBudget(),
                        "notes", "交通设施和空间距离只作为可达性参考，真实路线由后端调用高德路线 API 计算。WALK_BUS 只参考 BUS 设施，WALK_SUBWAY/BIKE_SUBWAY 只参考 SUBWAY 设施，WALK_TRANSIT 可混合参考 BUS/SUBWAY。"
                ),
                "districtBudget", districtPlan.effectiveDistrictBudget(),
                "districtOrder", districtPlan.districtOrder(),
                "districts", this.districtPayloads(districtPlan, candidatesById, context.getGenerateParam().getTransportProfile())
        );
    }

    private Map<String, Object> requestPayload(RouteGenerationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("areaMode", context.getGenerateParam().getAreaMode());
        payload.put("areaLabel", context.getArea().areaLabel());
        payload.put("routeCityName", context.getGenerateParam().getRouteCityName());
        payload.put("routeCityAdcode", context.getGenerateParam().getRouteCityAdcode());
        payload.put("departureTime", context.getGenerateParam().getDepartureTime());
        payload.put("durationMinutes", context.getGenerateParam().getDurationMinutes());
        payload.put("transportProfile", context.getGenerateParam().getTransportProfile());
        payload.put("routeGoal", context.getGenerateParam().getRouteGoal());
        payload.put("interestTags", context.getGenerateParam().getInterestTags());
        payload.put("mustVisitPoiIds", context.getPoiCandidates().stream()
                .filter(PoiCandidateDTO::mustVisit)
                .map(PoiCandidateDTO::poiId)
                .toList());
        payload.put("routeCountRange", Map.of("min", MAX_ROUTE_COUNT, "max", MAX_ROUTE_COUNT));
        return payload;
    }

    private List<Map<String, Object>> districtPayloads(
            RouteDistrictPlan districtPlan,
            Map<String, PoiCandidateDTO> candidatesById,
            TransportProfile transportProfile
    ) {
        Map<String, RouteDistrict> districtsById = new LinkedHashMap<>();
        for (RouteDistrict district : districtPlan.districts()) {
            districtsById.put(district.districtId(), district);
        }
        return districtPlan.districtOrder().stream()
                .map(districtsById::get)
                .filter(district -> district != null)
                .map(district -> this.districtPayload(district, candidatesById, transportProfile))
                .toList();
    }

    private Map<String, Object> districtPayload(
            RouteDistrict district,
            Map<String, PoiCandidateDTO> candidatesById,
            TransportProfile transportProfile
    ) {
        List<PoiCandidateDTO> districtCandidates = district.poiIds().stream()
                .map(candidatesById::get)
                .filter(candidate -> candidate != null)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("districtId", district.districtId());
        payload.put("centroid", this.locationPayload(district.centroid()));
        payload.put("poiCount", districtCandidates.size());
        payload.put("dominantInterests", this.dominantInterests(districtCandidates));
        payload.put("pois", districtCandidates.stream()
                .map(candidate -> this.poiPayload(candidate, transportProfile))
                .toList());
        return payload;
    }

    private List<String> dominantInterests(List<PoiCandidateDTO> candidates) {
        return candidates.stream()
                .flatMap(candidate -> candidate.matchedInterestTags().stream())
                .distinct()
                .limit(3)
                .toList();
    }

    private Map<String, Object> poiPayload(PoiCandidateDTO candidate, TransportProfile transportProfile) {
        List<TransitFacilityDTO> nearestTransit = this.relevantTransit(candidate, transportProfile);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("poiId", candidate.poiId());
        payload.put("amapPoiId", candidate.amapPoiId());
        payload.put("name", candidate.name());
        payload.put("category", candidate.category());
        payload.put("role", candidate.role());
        payload.put("location", this.locationPayload(candidate.location()));
        payload.put("address", candidate.address());
        payload.put("description", candidate.description());
        payload.put("rating", candidate.amapRating());
        payload.put("avgPriceCent", candidate.avgPriceCent());
        payload.put("tags", candidate.matchedInterestTags());
        payload.put("features", this.featuresPayload(candidate));
        payload.put("nearestTransit", nearestTransit);
        payload.put("transitAccessibility", this.transitAccessibility(candidate, nearestTransit));
        payload.put("mustVisit", candidate.mustVisit());
        payload.put("reasonSeed", candidate.reasonSeed());
        return payload;
    }

    private List<String> featuresPayload(PoiCandidateDTO candidate) {
        Set<String> features = new LinkedHashSet<>(candidate.matchedInterestTags());
        if (candidate.keytag() != null && !candidate.keytag().isBlank()) {
            features.add(candidate.keytag());
        }
        if (candidate.rectag() != null && !candidate.rectag().isBlank()) {
            features.add(candidate.rectag());
        }
        if (candidate.rawType() != null && !candidate.rawType().isBlank()) {
            features.add(candidate.rawType());
        }
        if (candidate.mustVisit()) {
            features.add("用户指定必去点");
        }
        return List.copyOf(features);
    }

    private Map<String, Object> locationPayload(GeoPointDTO location) {
        return Map.of(
                "longitudeGcj02", location.longitudeGcj02(),
                "latitudeGcj02", location.latitudeGcj02()
        );
    }

    private List<TransitFacilityDTO> relevantTransit(PoiCandidateDTO candidate, TransportProfile transportProfile) {
        List<TransitFacilityDTO> nearestTransit = candidate.nearestTransit();
        if (nearestTransit == null || nearestTransit.isEmpty()) {
            return List.of();
        }
        String requiredType = this.requiredTransitType(transportProfile);
        if (requiredType == null) {
            return nearestTransit;
        }
        return nearestTransit.stream()
                .filter(transit -> requiredType.equals(transit.type()))
                .toList();
    }

    private String transitAccessibility(PoiCandidateDTO candidate, List<TransitFacilityDTO> nearestTransit) {
        if (nearestTransit == null || nearestTransit.isEmpty()) {
            return this.transportUnavailable(candidate) ? "UNKNOWN" : "LOW";
        }
        Integer nearestMeters = nearestTransit.get(0).distanceMeters();
        if (nearestMeters == null) {
            return this.transportUnavailable(candidate) ? "UNKNOWN" : "LOW";
        }
        if (nearestMeters <= 300) {
            return "HIGH";
        }
        if (nearestMeters <= 800) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean transportUnavailable(PoiCandidateDTO candidate) {
        return candidate.transitLookupStatus() == TransitLookupStatus.UNAVAILABLE
                || candidate.transitLookupStatus() == TransitLookupStatus.FAILED;
    }

    private String requiredTransitType(TransportProfile transportProfile) {
        if (transportProfile == null) {
            return null;
        }
        return switch (transportProfile) {
            case WALK_BUS -> TRANSIT_TYPE_BUS;
            case WALK_SUBWAY, BIKE_SUBWAY -> TRANSIT_TYPE_SUBWAY;
            case WALK_ONLY, WALK_TRANSIT, WALK_TAXI -> null;
        };
    }
}
