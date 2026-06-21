package com.urbansidequest.backend.handler.route.district;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.linear.LinearScoreConstants;
import com.urbansidequest.backend.handler.route.support.GeoMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RouteDistrictPlanner {

    public RouteDistrictPlan plan(RouteGenerationContext context) {
        List<PoiCandidateDTO> candidates = context.getPoiCandidates();
        if (candidates.isEmpty()) {
            return new RouteDistrictPlan(List.of(), List.of(), Map.of(), Set.of(), 1, 1);
        }

        UnionFind unionFind = new UnionFind(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                GeoPointDTO left = candidates.get(i).location();
                GeoPointDTO right = candidates.get(j).location();
                if (left != null && right != null
                        && GeoMath.distanceMeters(left, right) <= LinearScoreConstants.SHORT_WALK_SEGMENT_METERS) {
                    unionFind.union(i, j);
                }
            }
        }

        Map<Integer, List<PoiCandidateDTO>> componentCandidates = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            componentCandidates.computeIfAbsent(unionFind.find(index), ignored -> new ArrayList<>()).add(candidates.get(index));
        }

        GeoPointDTO anchor = this.anchorPoint(context, candidates);
        List<RouteDistrict> districts = new ArrayList<>();
        Map<String, String> poiDistrictIds = new LinkedHashMap<>();
        Set<String> requiredDistrictIds = new LinkedHashSet<>();
        int districtIndex = 1;
        for (List<PoiCandidateDTO> districtCandidates : componentCandidates.values()) {
            String districtId = "D" + districtIndex++;
            List<String> poiIds = districtCandidates.stream().map(PoiCandidateDTO::poiId).toList();
            RouteDistrict district = new RouteDistrict(districtId, this.centroid(districtCandidates, anchor), poiIds);
            districts.add(district);
            for (PoiCandidateDTO candidate : districtCandidates) {
                poiDistrictIds.put(candidate.poiId(), districtId);
                if (candidate.mustVisit()) {
                    requiredDistrictIds.add(districtId);
                }
            }
        }

        int baseBudget = this.baseDistrictBudget(context);
        int effectiveBudget = Math.max(baseBudget, requiredDistrictIds.size());
        return new RouteDistrictPlan(
                districts,
                this.districtOrder(districts, requiredDistrictIds, anchor),
                poiDistrictIds,
                requiredDistrictIds,
                baseBudget,
                effectiveBudget
        );
    }

    private int baseDistrictBudget(RouteGenerationContext context) {
        TransportProfile profile = context.getGenerateParam().getTransportProfile();
        DurationBucket bucket = DurationBucket.fromMinutes(context.getGenerateParam().getDurationMinutes());
        if (profile == null) {
            return 1;
        }
        return switch (profile) {
            case WALK_ONLY -> 1;
            case WALK_BUS -> switch (bucket) {
                case SHORT -> 1;
                case HALF_DAY -> 2;
                case FULL_DAY -> 3;
            };
            case WALK_SUBWAY -> switch (bucket) {
                case SHORT, HALF_DAY -> 2;
                case FULL_DAY -> 3;
            };
            case WALK_TRANSIT, BIKE_SUBWAY -> switch (bucket) {
                case SHORT -> 2;
                case HALF_DAY, FULL_DAY -> 3;
            };
            case WALK_TAXI -> switch (bucket) {
                case SHORT -> 2;
                case HALF_DAY -> 3;
                case FULL_DAY -> 4;
            };
        };
    }

    private List<String> districtOrder(List<RouteDistrict> districts, Set<String> requiredDistrictIds, GeoPointDTO anchor) {
        if (districts.isEmpty()) {
            return List.of();
        }
        Map<String, RouteDistrict> byId = new LinkedHashMap<>();
        for (RouteDistrict district : districts) {
            byId.put(district.districtId(), district);
        }

        RouteDistrict current = districts.stream()
                .filter(district -> requiredDistrictIds.isEmpty() || requiredDistrictIds.contains(district.districtId()))
                .min(Comparator.comparingInt(district -> GeoMath.distanceMeters(anchor, district.centroid())))
                .orElse(districts.get(0));
        List<String> order = new ArrayList<>();
        Set<String> remaining = new LinkedHashSet<>(byId.keySet());
        while (current != null) {
            order.add(current.districtId());
            remaining.remove(current.districtId());
            RouteDistrict from = current;
            current = remaining.stream()
                    .map(byId::get)
                    .min(Comparator.comparingInt(district -> GeoMath.distanceMeters(from.centroid(), district.centroid())))
                    .orElse(null);
        }
        return order;
    }

    private GeoPointDTO anchorPoint(RouteGenerationContext context, List<PoiCandidateDTO> candidates) {
        if (context.getArea() != null && context.getArea().center() != null) {
            return context.getArea().center();
        }
        GeoPointParam center = context.getGenerateParam().getCenter();
        if (center != null && center.getLongitudeGcj02() != null && center.getLatitudeGcj02() != null) {
            return new GeoPointDTO(center.getLongitudeGcj02(), center.getLatitudeGcj02());
        }
        return this.centroid(candidates, new GeoPointDTO(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private GeoPointDTO centroid(List<PoiCandidateDTO> candidates, GeoPointDTO fallback) {
        List<GeoPointDTO> locations = candidates.stream()
                .map(PoiCandidateDTO::location)
                .filter(location -> location != null)
                .toList();
        if (locations.isEmpty()) {
            return fallback;
        }
        double longitude = locations.stream().mapToDouble(location -> location.longitudeGcj02().doubleValue()).average().orElse(0d);
        double latitude = locations.stream().mapToDouble(location -> location.latitudeGcj02().doubleValue()).average().orElse(0d);
        return new GeoPointDTO(BigDecimal.valueOf(longitude), BigDecimal.valueOf(latitude));
    }

    private static final class UnionFind {

        private final int[] parents;

        private UnionFind(int size) {
            this.parents = new int[size];
            for (int index = 0; index < size; index++) {
                this.parents[index] = index;
            }
        }

        private int find(int value) {
            if (this.parents[value] != value) {
                this.parents[value] = this.find(this.parents[value]);
            }
            return this.parents[value];
        }

        private void union(int left, int right) {
            int leftRoot = this.find(left);
            int rightRoot = this.find(right);
            if (leftRoot != rightRoot) {
                this.parents[rightRoot] = leftRoot;
            }
        }
    }
}
