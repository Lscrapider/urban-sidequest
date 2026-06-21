package com.urbansidequest.backend.handler.route.district;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RouteDistrictPlan(
        List<RouteDistrict> districts,
        List<String> districtOrder,
        Map<String, String> poiDistrictIds,
        Set<String> requiredDistrictIds,
        int baseDistrictBudget,
        int effectiveDistrictBudget
) {

    public RouteDistrictPlan {
        districts = districts == null ? List.of() : List.copyOf(districts);
        districtOrder = districtOrder == null ? List.of() : List.copyOf(districtOrder);
        poiDistrictIds = poiDistrictIds == null ? Map.of() : Map.copyOf(poiDistrictIds);
        requiredDistrictIds = requiredDistrictIds == null ? Set.of() : Set.copyOf(requiredDistrictIds);
    }

    public String districtIdOf(String poiId) {
        return this.poiDistrictIds.get(poiId);
    }
}
