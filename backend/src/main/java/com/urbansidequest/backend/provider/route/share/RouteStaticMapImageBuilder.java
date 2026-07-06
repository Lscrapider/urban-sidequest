package com.urbansidequest.backend.provider.route.share;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.config.AmapWebProperties;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.GeoPointVO;
import com.urbansidequest.backend.domain.vo.RouteSegmentVO;
import com.urbansidequest.backend.domain.vo.RouteStopVO;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RouteStaticMapImageBuilder {

    private static final int STATIC_MAP_WIDTH = 720;

    private static final int STATIC_MAP_HEIGHT = 560;

    private static final int STATIC_MAP_TILE_SIZE = 256;

    private static final int MAX_STATIC_MAP_POINTS = 90;

    private static final int MAX_STATIC_MAP_MARKERS = 10;

    private static final int MIN_STATIC_MAP_ZOOM = 3;

    private static final int MAX_STATIC_MAP_ZOOM = 17;

    private static final double STATIC_MAP_FIT_RATIO = 0.78d;

    private final AmapWebProperties amapWebProperties;

    public RouteStaticMapImageBuilder(AmapWebProperties amapWebProperties) {
        this.amapWebProperties = amapWebProperties;
    }

    public byte[] build(GeneratedRouteVO route) {
        List<GeoPointVO> routePoints = this.collectRoutePoints(route);
        List<GeoPointVO> sampledRoutePoints = this.samplePoints(routePoints);
        List<GeoPointVO> markerPoints = this.collectMarkerPoints(route, routePoints);
        List<GeoPointVO> fitPoints = new ArrayList<>(routePoints);
        fitPoints.addAll(markerPoints);
        if (sampledRoutePoints.isEmpty()) {
            throw new IllegalArgumentException("路线缺少地图坐标，无法生成分享图");
        }
        String key = this.amapWebProperties.effectiveKeys().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("高德 Web Key 未配置"));
        GeoPointVO center = this.centerOf(fitPoints.isEmpty() ? sampledRoutePoints : fitPoints);
        String markersParam = markerPoints.isEmpty() ? "" : "&markers=%s".formatted(this.encode(this.markersValue(markerPoints)));
        String url = "%s/v3/staticmap?location=%s&zoom=%d&size=%d*%d&scale=2&paths=%s%s&key=%s".formatted(
                StrUtil.removeSuffix(this.amapWebProperties.getBaseUrl(), "/"),
                this.encode("%s,%s".formatted(center.longitudeGcj02(), center.latitudeGcj02())),
                this.zoomOf(fitPoints.isEmpty() ? sampledRoutePoints : fitPoints),
                STATIC_MAP_WIDTH,
                STATIC_MAP_HEIGHT,
                this.encode(this.pathValue(sampledRoutePoints)),
                markersParam,
                this.encode(key)
        );
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout((int) this.amapWebProperties.getConnectTimeout().toMillis());
            connection.setReadTimeout((int) this.amapWebProperties.getReadTimeout().toMillis());
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("静态地图生成失败");
            }
            String contentType = StrUtil.blankToDefault(connection.getContentType(), "").toLowerCase();
            if (!contentType.startsWith("image/")) {
                throw new IllegalStateException("静态地图生成失败，请检查高德 Web Key 或路线坐标");
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return inputStream.readAllBytes();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("静态地图生成失败", exception);
        }
    }

    private List<GeoPointVO> collectRoutePoints(GeneratedRouteVO route) {
        List<GeoPointVO> points = new ArrayList<>();
        List<RouteSegmentVO> segments = route.segments() == null ? List.of() : route.segments();
        for (RouteSegmentVO segment : segments) {
            if (segment.polyline() != null && !segment.polyline().isEmpty()) {
                points.addAll(segment.polyline());
            } else if (segment.steps() != null) {
                segment.steps().stream()
                        .filter(step -> step.polyline() != null)
                        .flatMap(step -> step.polyline().stream())
                        .forEach(points::add);
            }
        }
        if (points.isEmpty()) {
            List<RouteStopVO> stops = route.stops() == null ? List.of() : route.stops();
            stops.stream()
                    .sorted(Comparator.comparingInt(RouteStopVO::order))
                    .map(RouteStopVO::location)
                    .forEach(points::add);
        }
        return points.stream()
                .filter(point -> point != null && point.longitudeGcj02() != null && point.latitudeGcj02() != null)
                .toList();
    }

    private List<GeoPointVO> collectMarkerPoints(GeneratedRouteVO route, List<GeoPointVO> routePoints) {
        List<RouteStopVO> stops = route.stops() == null ? List.of() : route.stops();
        List<GeoPointVO> stopPoints = stops.stream()
                .sorted(Comparator.comparingInt(RouteStopVO::order))
                .map(RouteStopVO::location)
                .filter(point -> point != null && point.longitudeGcj02() != null && point.latitudeGcj02() != null)
                .limit(MAX_STATIC_MAP_MARKERS)
                .toList();
        if (!stopPoints.isEmpty()) {
            return stopPoints;
        }
        if (routePoints.isEmpty()) {
            return List.of();
        }
        if (routePoints.size() == 1) {
            return List.of(routePoints.get(0));
        }
        return List.of(routePoints.get(0), routePoints.get(routePoints.size() - 1));
    }

    private List<GeoPointVO> samplePoints(List<GeoPointVO> points) {
        if (points.size() <= MAX_STATIC_MAP_POINTS) {
            return points;
        }
        List<GeoPointVO> sampled = new ArrayList<>();
        double step = (points.size() - 1) / (double) (MAX_STATIC_MAP_POINTS - 1);
        for (int index = 0; index < MAX_STATIC_MAP_POINTS; index++) {
            sampled.add(points.get((int) Math.round(index * step)));
        }
        return sampled;
    }

    private GeoPointVO centerOf(List<GeoPointVO> points) {
        double minLon = points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).min().orElse(0d);
        double maxLon = points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).max().orElse(0d);
        double minLat = points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).min().orElse(0d);
        double maxLat = points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).max().orElse(0d);
        return new GeoPointVO(
                BigDecimal.valueOf((minLon + maxLon) / 2d),
                BigDecimal.valueOf((minLat + maxLat) / 2d)
        );
    }

    private int zoomOf(List<GeoPointVO> points) {
        if (points.size() <= 1) {
            return MAX_STATIC_MAP_ZOOM;
        }
        double minLon = points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).min().orElse(0d);
        double maxLon = points.stream().mapToDouble(point -> point.longitudeGcj02().doubleValue()).max().orElse(0d);
        double minLat = points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).min().orElse(0d);
        double maxLat = points.stream().mapToDouble(point -> point.latitudeGcj02().doubleValue()).max().orElse(0d);
        double lonSpan = Math.abs(maxLon - minLon);
        double latFraction = Math.abs(this.mercatorFraction(maxLat) - this.mercatorFraction(minLat));
        int lonZoom = this.zoomForFraction(lonSpan / 360d, STATIC_MAP_WIDTH);
        int latZoom = this.zoomForFraction(latFraction, STATIC_MAP_HEIGHT);
        return Math.min(lonZoom, latZoom);
    }

    private int zoomForFraction(double fraction, int viewportSize) {
        if (fraction <= 0d) {
            return MAX_STATIC_MAP_ZOOM;
        }
        double fittedViewportSize = viewportSize * STATIC_MAP_FIT_RATIO;
        int zoom = (int) Math.floor(Math.log(fittedViewportSize / (STATIC_MAP_TILE_SIZE * fraction)) / Math.log(2d));
        return Math.max(MIN_STATIC_MAP_ZOOM, Math.min(MAX_STATIC_MAP_ZOOM, zoom));
    }

    private double mercatorFraction(double latitude) {
        double clampedLatitude = Math.max(-85.05112878d, Math.min(85.05112878d, latitude));
        double sinLatitude = Math.sin(Math.toRadians(clampedLatitude));
        return (0.5d - Math.log((1d + sinLatitude) / (1d - sinLatitude)) / (4d * Math.PI));
    }

    private String pathValue(List<GeoPointVO> points) {
        String locations = points.stream()
                .map(point -> "%s,%s".formatted(point.longitudeGcj02(), point.latitudeGcj02()))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        return "7,0x1373E6,0.85,,:" + locations;
    }

    private String markersValue(List<GeoPointVO> points) {
        List<String> markerValues = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            GeoPointVO point = points.get(index);
            String label = String.valueOf((char) ('A' + index));
            markerValues.add("mid,0x1373E6,%s:%s,%s".formatted(label, point.longitudeGcj02(), point.latitudeGcj02()));
        }
        return String.join("|", markerValues);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
