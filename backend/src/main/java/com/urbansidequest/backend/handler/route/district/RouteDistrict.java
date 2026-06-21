package com.urbansidequest.backend.handler.route.district;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import java.util.List;

public record RouteDistrict(
        String districtId,
        GeoPointDTO centroid,
        List<String> poiIds
) {

    public RouteDistrict {
        poiIds = poiIds == null ? List.of() : List.copyOf(poiIds);
    }
}
