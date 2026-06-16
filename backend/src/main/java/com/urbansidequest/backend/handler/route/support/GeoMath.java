package com.urbansidequest.backend.handler.route.support;

import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GeoMath {

    private static final double EARTH_RADIUS_METERS = 6371000d;

    private static final double METERS_PER_DEGREE = 111320d;

    private GeoMath() {
    }

    public static int distanceMeters(GeoPointDTO from, GeoPointDTO to) {
        double fromLat = from.latitudeGcj02().doubleValue();
        double toLat = to.latitudeGcj02().doubleValue();
        double deltaLat = Math.toRadians(toLat - fromLat);
        double deltaLng = Math.toRadians(to.longitudeGcj02().doubleValue() - from.longitudeGcj02().doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }

    public static GeoPointDTO offset(GeoPointDTO center, int eastMeters, int northMeters) {
        double lat = center.latitudeGcj02().doubleValue();
        double deltaLat = northMeters / METERS_PER_DEGREE;
        double deltaLng = eastMeters / (METERS_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        return new GeoPointDTO(
                BigDecimal.valueOf(center.longitudeGcj02().doubleValue() + deltaLng).setScale(7, RoundingMode.HALF_UP),
                BigDecimal.valueOf(center.latitudeGcj02().doubleValue() + deltaLat).setScale(7, RoundingMode.HALF_UP)
        );
    }
}
