package com.urbansidequest.backend.service.route;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.DurationBucket;
import com.urbansidequest.backend.domain.param.GeoPointParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RouteAreaPolicy {

    public RouteAreaDTO resolve(RouteGenerateParam generateParam) {
        return switch (generateParam.getAreaMode()) {
            case AUTO_RADIUS -> this.resolveAutoRadius(generateParam);
            case MANUAL_POLYGON -> this.resolveManualPolygon(generateParam);
            case ADMIN_DISTRICTS -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行政区组合范围暂未开放");
        };
    }

    private RouteAreaDTO resolveAutoRadius(RouteGenerateParam generateParam) {
        if (generateParam.getCenter() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "自动范围需要中心点");
        }
        GeoPointDTO center = this.toGeoPoint(generateParam.getCenter());
        int radiusMeters = this.resolveRadiusMeters(generateParam);
        List<GeoPointDTO> polygon = this.buildRectanglePolygon(center, radiusMeters);
        String areaLabel = generateParam.getAreaLabel() == null ? "当前位置附近" : generateParam.getAreaLabel();
        return new RouteAreaDTO(AreaMode.AUTO_RADIUS, areaLabel, center, radiusMeters, polygon);
    }

    private RouteAreaDTO resolveManualPolygon(RouteGenerateParam generateParam) {
        List<GeoPointParam> polygonParam = generateParam.getAreaPolygonGcj02();
        if (polygonParam == null || polygonParam.size() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手动框选范围至少需要 4 个坐标点");
        }
        List<GeoPointDTO> polygon = polygonParam.stream().map(this::toGeoPoint).toList();
        GeoPointDTO first = polygon.get(0);
        GeoPointDTO last = polygon.get(polygon.size() - 1);
        if (!first.equals(last)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手动框选范围需要闭合 polygon");
        }
        String areaLabel = generateParam.getAreaLabel() == null ? "手动框选区域" : generateParam.getAreaLabel();
        return new RouteAreaDTO(AreaMode.MANUAL_POLYGON, areaLabel, polygon.get(0), 0, polygon);
    }

    private int resolveRadiusMeters(RouteGenerateParam generateParam) {
        if (generateParam.getRadiusMeters() != null) {
            return generateParam.getRadiusMeters();
        }
        DurationBucket durationBucket = DurationBucket.fromMinutes(generateParam.getDurationMinutes());
        return generateParam.getTransportProfile().defaultRadiusMeters(durationBucket);
    }

    private List<GeoPointDTO> buildRectanglePolygon(GeoPointDTO center, int radiusMeters) {
        GeoPointDTO southWest = GeoMath.offset(center, -radiusMeters, -radiusMeters);
        GeoPointDTO southEast = GeoMath.offset(center, radiusMeters, -radiusMeters);
        GeoPointDTO northEast = GeoMath.offset(center, radiusMeters, radiusMeters);
        GeoPointDTO northWest = GeoMath.offset(center, -radiusMeters, radiusMeters);
        return List.of(southWest, southEast, northEast, northWest, southWest);
    }

    private GeoPointDTO toGeoPoint(GeoPointParam pointParam) {
        return new GeoPointDTO(pointParam.getLongitudeGcj02(), pointParam.getLatitudeGcj02());
    }
}
