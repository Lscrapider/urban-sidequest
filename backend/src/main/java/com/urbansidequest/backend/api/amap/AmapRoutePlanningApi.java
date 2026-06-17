package com.urbansidequest.backend.api.amap;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.config.AmapWebProperties;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RoutePlanDTO;
import com.urbansidequest.backend.domain.dto.RouteStepDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AmapRoutePlanningApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmapRoutePlanningApi.class);

    private static final String STATUS_SUCCESS = "1";

    private final AmapWebProperties amapWebProperties;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    public AmapRoutePlanningApi(
            AmapWebProperties amapWebProperties,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper
    ) {
        this.amapWebProperties = amapWebProperties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(amapWebProperties.getConnectTimeout())
                .readTimeout(amapWebProperties.getReadTimeout())
                .build();
        this.objectMapper = objectMapper;
    }

    public Optional<RoutePlanDTO> plan(
            GeoPointDTO origin,
            GeoPointDTO destination,
            SegmentTransportMode mode,
            String cityName,
            String cityAdcode
    ) {
        if (!this.isAvailable() || !this.supports(mode, cityName, cityAdcode)) {
            return Optional.empty();
        }
        try {
            JsonNode response = this.restTemplate.getForObject(
                    this.buildUri(origin, destination, mode, cityName, cityAdcode),
                    JsonNode.class
            );
            return this.parseRoutePlan(response, mode);
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "高德路径规划请求失败，mode={}，cityName={}，cityAdcode={}",
                    mode,
                    cityName,
                    cityAdcode,
                    exception
            );
            return Optional.empty();
        }
    }

    public Optional<RoutePlanDTO> parseCachedPlan(String rawPayload, SegmentTransportMode mode) {
        if (StrUtil.isBlank(rawPayload)) {
            return Optional.empty();
        }
        try {
            return this.parseRoutePlan(this.objectMapper.readTree(rawPayload), mode);
        } catch (Exception exception) {
            LOGGER.warn("高德路径规划缓存解析失败，mode={}", mode, exception);
            return Optional.empty();
        }
    }

    public boolean isAvailable() {
        return StrUtil.isNotBlank(this.amapWebProperties.getKey());
    }

    private boolean supports(SegmentTransportMode mode, String cityName, String cityAdcode) {
        return SegmentTransportMode.WALK == mode
                || SegmentTransportMode.BIKE == mode
                || SegmentTransportMode.TAXI == mode
                || SegmentTransportMode.DRIVE == mode
                || ((SegmentTransportMode.TRANSIT == mode
                || SegmentTransportMode.SUBWAY == mode
                || SegmentTransportMode.BUS == mode)
                && (StrUtil.isNotBlank(cityAdcode) || StrUtil.isNotBlank(cityName)));
    }

    private URI buildUri(
            GeoPointDTO origin,
            GeoPointDTO destination,
            SegmentTransportMode mode,
            String cityName,
            String cityAdcode
    ) {
        String path = switch (mode) {
            case WALK -> "/v3/direction/walking";
            case BIKE -> "/v4/direction/bicycling";
            case TAXI, DRIVE -> "/v3/direction/driving";
            case SUBWAY, BUS, TRANSIT -> "/v3/direction/transit/integrated";
        };
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(this.amapWebProperties.getBaseUrl())
                .path(path)
                .queryParam("origin", this.toLocation(origin))
                .queryParam("destination", this.toLocation(destination))
                .queryParam("key", this.amapWebProperties.getKey());
        if (this.isTransitMode(mode)) {
            String city = StrUtil.blankToDefault(cityAdcode, cityName);
            builder.queryParam("city", city).queryParam("cityd", city);
        }
        return builder
                .build()
                .toUri();
    }

    private Optional<RoutePlanDTO> parseRoutePlan(JsonNode response, SegmentTransportMode mode) {
        if (response == null || !STATUS_SUCCESS.equals(response.path("status").asText())) {
            this.logUnsuccessfulResponse(response, mode);
            return Optional.empty();
        }
        if (this.isTransitMode(mode)) {
            return this.parseTransitRoutePlan(response, mode);
        }
        JsonNode path = this.firstPath(response);
        if (path == null || path.isMissingNode()) {
            return Optional.empty();
        }
        List<RouteStepDTO> steps = this.parseSteps(path.path("steps"));
        List<GeoPointDTO> polyline = this.mergeStepPolylines(steps);
        if (polyline.isEmpty()) {
            polyline = this.parsePolyline(path.path("polyline").asText(""));
        }
        if (polyline.isEmpty()) {
            return Optional.empty();
        }
        int distanceMeters = this.readInt(path.path("distance"));
        if (distanceMeters <= 0) {
            distanceMeters = steps.stream().mapToInt(RouteStepDTO::distanceMeters).sum();
        }
        int durationMinutes = this.toMinutes(this.readInt(path.path("duration")));
        if (durationMinutes <= 0) {
            durationMinutes = steps.stream().mapToInt(RouteStepDTO::durationMinutes).sum();
        }
        return Optional.of(new RoutePlanDTO(
                distanceMeters,
                Math.max(1, durationMinutes),
                polyline,
                steps,
                this.summary(mode, distanceMeters, Math.max(1, durationMinutes)),
                response.toString()
        ));
    }

    private void logUnsuccessfulResponse(JsonNode response, SegmentTransportMode mode) {
        if (response == null) {
            LOGGER.warn("高德路径规划返回空响应，mode={}", mode);
            return;
        }
        LOGGER.warn(
                "高德路径规划返回失败，mode={}，status={}，info={}，infocode={}",
                mode,
                response.path("status").asText(""),
                response.path("info").asText(""),
                response.path("infocode").asText("")
        );
    }

    private Optional<RoutePlanDTO> parseTransitRoutePlan(JsonNode response, SegmentTransportMode mode) {
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
                this.appendTransitWalkingSteps(segment.path("walking").path("steps"), steps, polyline);
                this.appendTransitBusSteps(segment.path("bus").path("buslines"), steps, polyline);
            }
        }
        if (polyline.isEmpty()) {
            return Optional.empty();
        }
        int distanceMeters = this.readInt(transit.path("distance"));
        if (distanceMeters <= 0) {
            distanceMeters = steps.stream().mapToInt(RouteStepDTO::distanceMeters).sum();
        }
        int durationMinutes = this.toMinutes(this.readInt(transit.path("duration")));
        if (durationMinutes <= 0) {
            durationMinutes = steps.stream().mapToInt(RouteStepDTO::durationMinutes).sum();
        }
        return Optional.of(new RoutePlanDTO(
                distanceMeters,
                Math.max(1, durationMinutes),
                polyline,
                steps,
                this.summary(mode, distanceMeters, Math.max(1, durationMinutes)),
                response.toString()
        ));
    }

    private void appendTransitWalkingSteps(
            JsonNode walkingSteps,
            List<RouteStepDTO> steps,
            List<GeoPointDTO> polyline
    ) {
        if (!walkingSteps.isArray()) {
            return;
        }
        for (int index = 0; index < walkingSteps.size(); index++) {
            JsonNode step = walkingSteps.get(index);
            List<GeoPointDTO> stepPolyline = this.parsePolyline(step.path("polyline").asText(""));
            this.appendPolyline(polyline, stepPolyline);
            steps.add(new RouteStepDTO(
                    steps.size() + 1,
                    step.path("instruction").asText("步行前往站点"),
                    step.path("road").asText(""),
                    this.readInt(step.path("distance")),
                    Math.max(1, this.toMinutes(this.readInt(step.path("duration")))),
                    stepPolyline
            ));
        }
    }

    private void appendTransitBusSteps(
            JsonNode buslines,
            List<RouteStepDTO> steps,
            List<GeoPointDTO> polyline
    ) {
        if (!buslines.isArray() || buslines.isEmpty()) {
            return;
        }
        JsonNode busline = buslines.get(0);
        List<GeoPointDTO> busPolyline = this.parsePolyline(busline.path("polyline").asText(""));
        this.appendPolyline(polyline, busPolyline);
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
                this.readInt(busline.path("distance")),
                Math.max(1, this.toMinutes(this.readInt(busline.path("duration")))),
                busPolyline
        ));
    }

    private void appendPolyline(List<GeoPointDTO> target, List<GeoPointDTO> source) {
        for (GeoPointDTO point : source) {
            if (target.isEmpty() || !target.get(target.size() - 1).equals(point)) {
                target.add(point);
            }
        }
    }

    private JsonNode firstPath(JsonNode response) {
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

    private List<RouteStepDTO> parseSteps(JsonNode stepsNode) {
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
                    this.readInt(step.path("distance")),
                    Math.max(1, this.toMinutes(this.readInt(step.path("duration")))),
                    this.parsePolyline(step.path("polyline").asText(""))
            ));
        }
        return steps;
    }

    private List<GeoPointDTO> mergeStepPolylines(List<RouteStepDTO> steps) {
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

    private List<GeoPointDTO> parsePolyline(String polylineText) {
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

    private int readInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        return StrUtil.isBlank(node.asText()) ? 0 : Integer.parseInt(node.asText());
    }

    private int toMinutes(int seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }

    private String summary(SegmentTransportMode mode, int distanceMeters, int durationMinutes) {
        return this.modeLabel(mode) + "约 " + Math.max(1, durationMinutes) + " 分钟，" + distanceMeters + " 米";
    }

    private String modeLabel(SegmentTransportMode mode) {
        return switch (mode) {
            case WALK -> "步行";
            case BIKE -> "骑行";
            case TAXI, DRIVE -> "驾车";
            case SUBWAY, BUS, TRANSIT -> "公共交通";
        };
    }

    private boolean isTransitMode(SegmentTransportMode mode) {
        return SegmentTransportMode.TRANSIT == mode
                || SegmentTransportMode.SUBWAY == mode
                || SegmentTransportMode.BUS == mode;
    }

    private String toLocation(GeoPointDTO point) {
        return point.longitudeGcj02().toPlainString() + "," + point.latitudeGcj02().toPlainString();
    }
}
