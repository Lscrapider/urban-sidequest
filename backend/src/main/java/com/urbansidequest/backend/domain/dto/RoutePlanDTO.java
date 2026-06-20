package com.urbansidequest.backend.domain.dto;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record RoutePlanDTO(
        Integer distanceMeters,
        Integer durationMinutes,
        List<GeoPointDTO> polyline,
        List<RouteStepDTO> steps,
        String summary,
        String rawPayload
) {

    private static final String STATUS_SUCCESS = "1";

    /**
     * 从高德路径规划响应解析路线计划；状态非成功、无可用路径或无 polyline 时返回空，由调用方降级。
     *
     * <p>本工厂负责把不同交通方式（步行/骑行/驾车/公交）的响应统一映射成路线计划，
     * api 层只发请求，不参与解析。
     */
    public static Optional<RoutePlanDTO> fromAmapResponse(JsonNode response, SegmentTransportMode mode) {
        if (response == null || !STATUS_SUCCESS.equals(response.path("status").asText())) {
            return Optional.empty();
        }
        if (isTransitMode(mode)) {
            return parseTransitRoutePlan(response, mode);
        }
        JsonNode path = firstPath(response);
        if (path == null || path.isMissingNode()) {
            return Optional.empty();
        }
        List<RouteStepDTO> steps = parseSteps(path.path("steps"));
        List<GeoPointDTO> polyline = mergeStepPolylines(steps);
        if (polyline.isEmpty()) {
            polyline = parsePolyline(path.path("polyline").asText(""));
        }
        if (polyline.isEmpty()) {
            return Optional.empty();
        }
        int distanceMeters = readInt(path.path("distance"));
        if (distanceMeters <= 0) {
            distanceMeters = steps.stream().mapToInt(RouteStepDTO::distanceMeters).sum();
        }
        int durationMinutes = toMinutes(readInt(path.path("duration")));
        if (durationMinutes <= 0) {
            durationMinutes = steps.stream().mapToInt(RouteStepDTO::durationMinutes).sum();
        }
        durationMinutes = Math.max(1, durationMinutes);
        return Optional.of(new RoutePlanDTO(
                distanceMeters,
                durationMinutes,
                polyline,
                steps,
                summary(mode, distanceMeters, durationMinutes),
                response.toString()
        ));
    }

    private static Optional<RoutePlanDTO> parseTransitRoutePlan(JsonNode response, SegmentTransportMode mode) {
        JsonNode transits = response.path("route").path("transits");
        if (!transits.isArray() || transits.isEmpty()) {
            return Optional.empty();
        }
        JsonNode transit = transits.get(0);
        List<RouteStepDTO> steps = new ArrayList<>();
        List<GeoPointDTO> polyline = new ArrayList<>();
        JsonNode segments = transit.path("segments");
        if (segments.isArray()) {
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                JsonNode segment = segments.get(segmentIndex);
                appendTransitWalkingSteps(segment.path("walking").path("steps"), steps, polyline);
                appendTransitBusSteps(segment.path("bus").path("buslines"), steps, polyline);
            }
        }
        if (polyline.isEmpty()) {
            return Optional.empty();
        }
        int distanceMeters = readInt(transit.path("distance"));
        if (distanceMeters <= 0) {
            distanceMeters = steps.stream().mapToInt(RouteStepDTO::distanceMeters).sum();
        }
        int durationMinutes = toMinutes(readInt(transit.path("duration")));
        if (durationMinutes <= 0) {
            durationMinutes = steps.stream().mapToInt(RouteStepDTO::durationMinutes).sum();
        }
        durationMinutes = Math.max(1, durationMinutes);
        return Optional.of(new RoutePlanDTO(
                distanceMeters,
                durationMinutes,
                polyline,
                steps,
                summary(mode, distanceMeters, durationMinutes),
                response.toString()
        ));
    }

    private static void appendTransitWalkingSteps(
            JsonNode walkingSteps,
            List<RouteStepDTO> steps,
            List<GeoPointDTO> polyline
    ) {
        if (!walkingSteps.isArray()) {
            return;
        }
        for (int index = 0; index < walkingSteps.size(); index++) {
            JsonNode step = walkingSteps.get(index);
            List<GeoPointDTO> stepPolyline = parsePolyline(step.path("polyline").asText(""));
            appendPolyline(polyline, stepPolyline);
            steps.add(new RouteStepDTO(
                    steps.size() + 1,
                    step.path("instruction").asText("步行前往站点"),
                    step.path("road").asText(""),
                    readInt(step.path("distance")),
                    Math.max(1, toMinutes(readInt(step.path("duration")))),
                    stepPolyline
            ));
        }
    }

    private static void appendTransitBusSteps(
            JsonNode buslines,
            List<RouteStepDTO> steps,
            List<GeoPointDTO> polyline
    ) {
        if (!buslines.isArray() || buslines.isEmpty()) {
            return;
        }
        JsonNode busline = buslines.get(0);
        List<GeoPointDTO> busPolyline = parsePolyline(busline.path("polyline").asText(""));
        appendPolyline(polyline, busPolyline);
        String lineName = busline.path("name").asText("公共交通");
        String departureStop = busline.path("departure_stop").path("name").asText("");
        String arrivalStop = busline.path("arrival_stop").path("name").asText("");
        String instruction = "乘坐 " + lineName;
        if (StrUtil.isNotBlank(departureStop) && StrUtil.isNotBlank(arrivalStop)) {
            instruction += "，从 " + departureStop + " 到 " + arrivalStop;
        }
        steps.add(new RouteStepDTO(
                steps.size() + 1,
                instruction,
                lineName,
                readInt(busline.path("distance")),
                Math.max(1, toMinutes(readInt(busline.path("duration")))),
                busPolyline
        ));
    }

    private static void appendPolyline(List<GeoPointDTO> target, List<GeoPointDTO> source) {
        for (GeoPointDTO point : source) {
            if (target.isEmpty() || !target.get(target.size() - 1).equals(point)) {
                target.add(point);
            }
        }
    }

    private static JsonNode firstPath(JsonNode response) {
        JsonNode v3Paths = response.path("route").path("paths");
        if (v3Paths.isArray() && !v3Paths.isEmpty()) {
            return v3Paths.get(0);
        }
        JsonNode v4Paths = response.path("data").path("paths");
        if (v4Paths.isArray() && !v4Paths.isEmpty()) {
            return v4Paths.get(0);
        }
        return null;
    }

    private static List<RouteStepDTO> parseSteps(JsonNode stepsNode) {
        if (!stepsNode.isArray()) {
            return List.of();
        }
        List<RouteStepDTO> steps = new ArrayList<>();
        for (int index = 0; index < stepsNode.size(); index++) {
            JsonNode step = stepsNode.get(index);
            steps.add(new RouteStepDTO(
                    index + 1,
                    step.path("instruction").asText("继续前行"),
                    step.path("road").asText(""),
                    readInt(step.path("distance")),
                    Math.max(1, toMinutes(readInt(step.path("duration")))),
                    parsePolyline(step.path("polyline").asText(""))
            ));
        }
        return steps;
    }

    private static List<GeoPointDTO> mergeStepPolylines(List<RouteStepDTO> steps) {
        List<GeoPointDTO> points = new ArrayList<>();
        for (RouteStepDTO step : steps) {
            for (GeoPointDTO point : step.polyline()) {
                if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) {
                    points.add(point);
                }
            }
        }
        return points;
    }

    private static List<GeoPointDTO> parsePolyline(String polylineText) {
        if (StrUtil.isBlank(polylineText)) {
            return List.of();
        }
        List<GeoPointDTO> points = new ArrayList<>();
        for (String pointText : polylineText.split(";")) {
            String[] parts = pointText.split(",");
            if (parts.length != 2 || StrUtil.isBlank(parts[0]) || StrUtil.isBlank(parts[1])) {
                continue;
            }
            points.add(new GeoPointDTO(new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim())));
        }
        return points;
    }

    private static int readInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        return StrUtil.isBlank(node.asText()) ? 0 : Integer.parseInt(node.asText());
    }

    private static int toMinutes(int seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }

    private static String summary(SegmentTransportMode mode, int distanceMeters, int durationMinutes) {
        return modeLabel(mode) + "约 " + Math.max(1, durationMinutes) + " 分钟，" + distanceMeters + " 米";
    }

    private static String modeLabel(SegmentTransportMode mode) {
        return switch (mode) {
            case WALK -> "步行";
            case BIKE -> "骑行";
            case TAXI, DRIVE -> "驾车";
            case SUBWAY, BUS, TRANSIT -> "公共交通";
        };
    }

    private static boolean isTransitMode(SegmentTransportMode mode) {
        return SegmentTransportMode.TRANSIT == mode
                || SegmentTransportMode.SUBWAY == mode
                || SegmentTransportMode.BUS == mode;
    }
}
