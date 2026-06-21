package com.urbansidequest.backend.handler.route.support;

public final class RouteStopIdSupport {

    private RouteStopIdSupport() {
    }

    public static String poiIdFromStopId(String stopId, String routeCode) {
        if (stopId == null || routeCode == null) {
            return stopId;
        }
        String suffix = "-" + routeCode;
        return stopId.endsWith(suffix) ? stopId.substring(0, stopId.length() - suffix.length()) : stopId;
    }
}
