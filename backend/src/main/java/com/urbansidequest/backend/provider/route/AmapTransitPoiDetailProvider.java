package com.urbansidequest.backend.provider.route;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.api.amap.AmapApi;
import com.urbansidequest.backend.domain.dto.AmapPoiSearchQueryDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.TransitFacilityDTO;
import com.urbansidequest.backend.domain.enums.TransitLookupStatus;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Primary
@Component
public class AmapTransitPoiDetailProvider implements PoiDetailProvider {

    private static final String SEARCH_TYPE_POLYGON = "POLYGON";

    private static final int PAGE_SIZE = 25;

    private static final int MAX_PAGE_NUM = 4;

    private static final int BOUNDS_BUFFER_METERS = 500;

    private static final int NEAREST_TRANSIT_LIMIT = 3;

    private static final int NEAREST_SUBWAY_LIMIT = 1;

    private static final int NEAREST_BUS_LIMIT = 2;

    private final AmapApi amapApi;

    public AmapTransitPoiDetailProvider(AmapApi amapApi) {
        this.amapApi = amapApi;
    }

    @Override
    public List<PoiCandidateDTO> enrichDetails(RouteGenerationContext context, List<PoiCandidateDTO> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        if (!this.amapApi.isAvailable()) {
            context.setTransportSignalAvailable(false);
            return this.withTransitStatus(candidates, TransitLookupStatus.UNAVAILABLE, "UNKNOWN");
        }
        List<TransitPoint> transitPoints;
        try {
            transitPoints = this.loadTransitPoints(candidates);
        } catch (RestClientException | IllegalArgumentException exception) {
            context.addWarning("高德交通设施查询失败，已跳过 POI 交通可达性增强");
            context.setTransportSignalAvailable(false);
            return this.withTransitStatus(candidates, TransitLookupStatus.FAILED, "UNKNOWN");
        }
        if (transitPoints.isEmpty()) {
            context.addWarning("当前 POI 范围内未查询到公交或地铁设施");
            context.setTransportSignalAvailable(true);
            return this.withTransitStatus(candidates, TransitLookupStatus.SUCCESS_EMPTY, "LOW");
        }
        context.setTransportSignalAvailable(true);
        return candidates.stream()
                .map(candidate -> this.withTransitAccess(candidate, transitPoints))
                .toList();
    }

    private List<TransitPoint> loadTransitPoints(List<PoiCandidateDTO> candidates) {
        List<GeoPointDTO> polygon = this.boundsPolygon(candidates);
        List<TransitPoint> transitPoints = new ArrayList<>();
        transitPoints.addAll(this.searchTransit(polygon, "SUBWAY", List.of("地铁站")));
        transitPoints.addAll(this.searchTransit(polygon, "BUS", List.of("公交站")));
        return transitPoints;
    }

    private List<TransitPoint> searchTransit(List<GeoPointDTO> polygon, String type, List<String> keywords) {
        List<TransitPoint> transitPoints = new ArrayList<>();
        for (int pageNum = 1; pageNum <= MAX_PAGE_NUM; pageNum++) {
            JsonNode response = this.amapApi.searchPoi(new AmapPoiSearchQueryDTO(
                    SEARCH_TYPE_POLYGON,
                    null,
                    null,
                    polygon,
                    List.of(),
                    keywords,
                    pageNum,
                    PAGE_SIZE
            ));
            if (!this.isSuccess(response)) {
                throw new IllegalArgumentException("高德交通设施查询返回失败");
            }
            JsonNode pois = response == null ? null : response.path("pois");
            if (pois == null || !pois.isArray() || pois.isEmpty()) {
                break;
            }
            for (JsonNode poi : pois) {
                this.toTransitPoint(poi, type).ifPresent(transitPoints::add);
            }
            if (pois.size() < PAGE_SIZE) {
                break;
            }
        }
        return transitPoints;
    }

    private boolean isSuccess(JsonNode response) {
        return response != null && "1".equals(response.path("status").asText());
    }

    private java.util.Optional<TransitPoint> toTransitPoint(JsonNode poi, String type) {
        String name = poi.path("name").asText();
        String locationText = poi.path("location").asText();
        if (StrUtil.isBlank(name) || StrUtil.isBlank(locationText) || !locationText.contains(",")) {
            return java.util.Optional.empty();
        }
        String[] parts = locationText.split(",", 2);
        return java.util.Optional.of(new TransitPoint(
                type,
                name,
                new GeoPointDTO(new BigDecimal(parts[0]), new BigDecimal(parts[1]))
        ));
    }

    private PoiCandidateDTO withTransitAccess(PoiCandidateDTO candidate, List<TransitPoint> transitPoints) {
        List<TransitFacilityDTO> ranked = transitPoints.stream()
                .map(transitPoint -> new TransitFacilityDTO(
                        transitPoint.type(),
                        transitPoint.name(),
                        GeoMath.distanceMeters(candidate.location(), transitPoint.location())
                ))
                .sorted(Comparator.comparing(TransitFacilityDTO::distanceMeters)
                        .thenComparing(TransitFacilityDTO::type))
                .toList();
        List<TransitFacilityDTO> subway = ranked.stream()
                .filter(transit -> "SUBWAY".equals(transit.type()))
                .limit(NEAREST_SUBWAY_LIMIT)
                .toList();
        List<TransitFacilityDTO> buses = ranked.stream()
                .filter(transit -> "BUS".equals(transit.type()))
                .limit(NEAREST_BUS_LIMIT)
                .toList();
        List<TransitFacilityDTO> nearestTransit = new ArrayList<>();
        nearestTransit.addAll(subway);
        nearestTransit.addAll(buses);
        nearestTransit = nearestTransit.stream()
                .sorted(Comparator.comparing(TransitFacilityDTO::distanceMeters))
                .limit(NEAREST_TRANSIT_LIMIT)
                .toList();
        return candidate.withTransitDetails(
                nearestTransit,
                this.transitAccessibility(nearestTransit),
                TransitLookupStatus.SUCCESS
        );
    }

    private List<PoiCandidateDTO> withTransitStatus(
            List<PoiCandidateDTO> candidates,
            TransitLookupStatus transitLookupStatus,
            String transitAccessibility
    ) {
        return candidates.stream()
                .map(candidate -> candidate.withTransitDetails(List.of(), transitAccessibility, transitLookupStatus))
                .toList();
    }

    private String transitAccessibility(List<TransitFacilityDTO> nearestTransit) {
        if (nearestTransit.isEmpty() || nearestTransit.get(0).distanceMeters() == null) {
            return "UNKNOWN";
        }
        int nearestDistanceMeters = nearestTransit.get(0).distanceMeters();
        if (nearestDistanceMeters <= 300) {
            return "HIGH";
        }
        if (nearestDistanceMeters <= 800) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<GeoPointDTO> boundsPolygon(List<PoiCandidateDTO> candidates) {
        double minLng = candidates.stream()
                .map(PoiCandidateDTO::location)
                .mapToDouble(location -> location.longitudeGcj02().doubleValue())
                .min()
                .orElseThrow();
        double maxLng = candidates.stream()
                .map(PoiCandidateDTO::location)
                .mapToDouble(location -> location.longitudeGcj02().doubleValue())
                .max()
                .orElseThrow();
        double minLat = candidates.stream()
                .map(PoiCandidateDTO::location)
                .mapToDouble(location -> location.latitudeGcj02().doubleValue())
                .min()
                .orElseThrow();
        double maxLat = candidates.stream()
                .map(PoiCandidateDTO::location)
                .mapToDouble(location -> location.latitudeGcj02().doubleValue())
                .max()
                .orElseThrow();

        GeoPointDTO southwest = GeoMath.offset(this.point(minLng, minLat), -BOUNDS_BUFFER_METERS, -BOUNDS_BUFFER_METERS);
        GeoPointDTO northwest = GeoMath.offset(this.point(minLng, maxLat), -BOUNDS_BUFFER_METERS, BOUNDS_BUFFER_METERS);
        GeoPointDTO northeast = GeoMath.offset(this.point(maxLng, maxLat), BOUNDS_BUFFER_METERS, BOUNDS_BUFFER_METERS);
        GeoPointDTO southeast = GeoMath.offset(this.point(maxLng, minLat), BOUNDS_BUFFER_METERS, -BOUNDS_BUFFER_METERS);
        return List.of(southwest, northwest, northeast, southeast);
    }

    private GeoPointDTO point(double longitude, double latitude) {
        return new GeoPointDTO(BigDecimal.valueOf(longitude), BigDecimal.valueOf(latitude));
    }

    private record TransitPoint(String type, String name, GeoPointDTO location) {
    }
}
