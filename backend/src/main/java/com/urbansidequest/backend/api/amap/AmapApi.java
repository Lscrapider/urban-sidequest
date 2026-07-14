package com.urbansidequest.backend.api.amap;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.config.AmapWebProperties;
import com.urbansidequest.backend.domain.dto.AmapAdministrativeRegionDTO;
import com.urbansidequest.backend.domain.dto.AmapPoiSearchQueryDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import com.urbansidequest.backend.domain.dto.RoutePlanDTO;
import com.urbansidequest.backend.domain.dto.RoutePlanResultDTO;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.enums.SegmentTransportMode;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 高德 Web 服务统一客户端。
 *
 * <p>按"同一第三方数据源归一类"组织：POI 搜索、路径规划、实况天气都是高德 Web 服务的不同资源，
 * 共用一个 {@link RestTemplate} 和 {@link AmapKeyPool}。客户端只负责取 key、拼 URL、发请求和异常降级，
 * 响应到 DTO 的解析与天气业务分级分别下沉到 {@link RoutePlanDTO}、{@link RouteWeatherDTO} 的静态工厂和枚举。
 *
 * <p>限速按 key 维度由 {@link AmapKeyPool} 统一控制：每次发起 HTTP 调用前取一个可用 key，
 * 因此 POI 搜索、路径规划、天气都会消耗对应 key 的配额。
 */
@Component
public class AmapApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmapApi.class);

    private static final String SEARCH_TYPE_AROUND = "AROUND";

    private static final String SEARCH_TYPE_POLYGON = "POLYGON";

    private static final String SHOW_FIELDS = "business,photos";

    private static final String EXTENSIONS_BASE = "base";

    private static final int ADMINISTRATIVE_REGION_PAGE_SIZE = 20;

    private static final String STATUS_SUCCESS = "1";

    private final AmapWebProperties amapWebProperties;

    private final AmapKeyPool amapKeyPool;

    private final AmapKeyFailureClassifier amapKeyFailureClassifier;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    public AmapApi(
            AmapWebProperties amapWebProperties,
            AmapKeyPool amapKeyPool,
            AmapKeyFailureClassifier amapKeyFailureClassifier,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper
    ) {
        this.amapWebProperties = amapWebProperties;
        this.amapKeyPool = amapKeyPool;
        this.amapKeyFailureClassifier = amapKeyFailureClassifier;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(amapWebProperties.getConnectTimeout())
                .readTimeout(amapWebProperties.getReadTimeout())
                .build();
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return this.amapKeyPool.isAvailable();
    }

    // ===== 行政区 =====

    /**
     * 查询高德行政区，并仅保留地区选择所需的一级子节点。
     *
     * <p>地区树由服务端持久化缓存，移动端不会直接持有高德 Web Key。
     */
    public List<AmapAdministrativeRegionDTO> queryAdministrativeRegions(String keywords) {
        if (!this.isAvailable()) {
            throw new IllegalStateException("行政区数据服务暂不可用");
        }
        try {
            JsonNode response = this.getForObjectWithHealthyKey(
                    key -> this.buildAdministrativeRegionUri(keywords, key),
                    "行政区查询"
            );
            if (response == null || !STATUS_SUCCESS.equals(response.path("status").asText())) {
                throw new IllegalStateException("行政区数据服务暂不可用");
            }
            JsonNode districts = response.path("districts");
            if (!districts.isArray()) {
                return List.of();
            }
            return java.util.stream.StreamSupport.stream(districts.spliterator(), false)
                    .map(this::toAdministrativeRegion)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("高德行政区查询失败，keywords={}", keywords, exception);
            throw new IllegalStateException("行政区数据服务暂不可用", exception);
        }
    }

    private URI buildAdministrativeRegionUri(String keywords, String key) {
        return UriComponentsBuilder.fromUriString(this.amapWebProperties.getBaseUrl())
                .path("/v3/config/district")
                .queryParam("key", key)
                .queryParam("keywords", keywords)
                .queryParam("subdistrict", 1)
                .queryParam("extensions", EXTENSIONS_BASE)
                .queryParam("offset", ADMINISTRATIVE_REGION_PAGE_SIZE)
                .queryParam("page", 1)
                .build()
                .toUri();
    }

    private Optional<AmapAdministrativeRegionDTO> toAdministrativeRegion(JsonNode node) {
        String adcode = node.path("adcode").asText("").trim();
        String name = node.path("name").asText("").trim();
        String center = node.path("center").asText("").trim();
        String[] coordinates = center.split(",");
        if (adcode.isBlank() || name.isBlank() || coordinates.length != 2) {
            return Optional.empty();
        }
        try {
            List<AmapAdministrativeRegionDTO> children = new java.util.ArrayList<>();
            JsonNode districts = node.path("districts");
            if (districts.isArray()) {
                districts.forEach(child -> this.toAdministrativeRegion(child).ifPresent(children::add));
            }
            return Optional.of(new AmapAdministrativeRegionDTO(
                    adcode,
                    name,
                    node.path("level").asText("district"),
                    new java.math.BigDecimal(coordinates[0].trim()),
                    new java.math.BigDecimal(coordinates[1].trim()),
                    children
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    // ===== POI 搜索 =====

    public JsonNode searchPoi(AmapPoiSearchQueryDTO query) {
        return this.getForObjectWithHealthyKey(key -> switch (query.searchType()) {
            case SEARCH_TYPE_AROUND -> this.buildAroundUri(query, key);
            case SEARCH_TYPE_POLYGON -> this.buildPolygonUri(query, key);
            default -> throw new IllegalArgumentException("不支持的高德 POI 搜索类型：" + query.searchType());
        }, "POI 搜索");
    }

    private URI buildAroundUri(AmapPoiSearchQueryDTO query, String key) {
        return this.poiBaseUriBuilder("/v5/place/around", key)
                .queryParam("location", this.toLocation(query.center()))
                .queryParam("radius", query.radiusMeters())
                .queryParam("types", this.join(query.types()))
                .queryParam("keywords", this.join(query.keywords()))
                .queryParam("page_num", query.pageNum())
                .queryParam("page_size", query.pageSize())
                .build()
                .toUri();
    }

    private URI buildPolygonUri(AmapPoiSearchQueryDTO query, String key) {
        return this.poiBaseUriBuilder("/v5/place/polygon", key)
                .queryParam("polygon", this.toPolygon(query.polygon()))
                .queryParam("types", this.join(query.types()))
                .queryParam("keywords", this.join(query.keywords()))
                .queryParam("page_num", query.pageNum())
                .queryParam("page_size", query.pageSize())
                .build()
                .toUri();
    }

    private UriComponentsBuilder poiBaseUriBuilder(String path, String key) {
        return UriComponentsBuilder.fromUriString(this.amapWebProperties.getBaseUrl())
                .path(path)
                .queryParam("key", key)
                .queryParam("show_fields", SHOW_FIELDS);
    }

    // ===== 路径规划 =====

    public Optional<RoutePlanDTO> planRoute(
            GeoPointDTO origin,
            GeoPointDTO destination,
            SegmentTransportMode mode,
            String cityName,
            String cityAdcode
    ) {
        return this.planRouteResult(origin, destination, mode, cityName, cityAdcode).planOptional();
    }

    public RoutePlanResultDTO planRouteResult(
            GeoPointDTO origin,
            GeoPointDTO destination,
            SegmentTransportMode mode,
            String cityName,
            String cityAdcode
    ) {
        if (!this.isAvailable()) {
            return RoutePlanResultDTO.temporaryFailure();
        }
        if (!this.supports(mode, cityName, cityAdcode)) {
            return RoutePlanResultDTO.unsupported();
        }
        try {
            JsonNode response = this.getForObjectWithHealthyKey(
                    key -> this.buildRouteUri(origin, destination, mode, cityName, cityAdcode, key),
                    "路径规划"
            );
            Optional<RoutePlanDTO> plan = RoutePlanDTO.fromAmapResponse(response, mode);
            if (plan.isEmpty()) {
                this.logUnusableRoute(response, mode);
                if (this.isNoRouteResponse(response, mode)) {
                    return RoutePlanResultDTO.noRoute();
                }
            }
            return plan.map(RoutePlanResultDTO::success).orElseGet(RoutePlanResultDTO::temporaryFailure);
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn(
                    "高德路径规划请求失败，mode={}，cityName={}，cityAdcode={}",
                    mode,
                    cityName,
                    cityAdcode,
                    exception
            );
            return RoutePlanResultDTO.temporaryFailure();
        }
    }

    public Optional<RoutePlanDTO> parseCachedPlan(String rawPayload, SegmentTransportMode mode) {
        if (StrUtil.isBlank(rawPayload)) {
            return Optional.empty();
        }
        try {
            return RoutePlanDTO.fromAmapResponse(this.objectMapper.readTree(rawPayload), mode);
        } catch (Exception exception) {
            LOGGER.warn("高德路径规划缓存解析失败，mode={}", mode, exception);
            return Optional.empty();
        }
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

    private URI buildRouteUri(
            GeoPointDTO origin,
            GeoPointDTO destination,
            SegmentTransportMode mode,
            String cityName,
            String cityAdcode,
            String key
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
                .queryParam("key", key);
        if (this.isTransitMode(mode)) {
            String city = StrUtil.blankToDefault(cityAdcode, cityName);
            builder.queryParam("city", city).queryParam("cityd", city);
        }
        return builder
                .build()
                .toUri();
    }

    private void logUnusableRoute(JsonNode response, SegmentTransportMode mode) {
        if (response == null) {
            LOGGER.warn("高德路径规划返回空响应，mode={}", mode);
            return;
        }
        LOGGER.warn(
                "高德路径规划无可用路线，mode={}，status={}，info={}，infocode={}",
                mode,
                response.path("status").asText(""),
                response.path("info").asText(""),
                response.path("infocode").asText("")
        );
    }

    private boolean isNoRouteResponse(JsonNode response, SegmentTransportMode mode) {
        if (response == null || !STATUS_SUCCESS.equals(response.path("status").asText())) {
            return false;
        }
        if (this.isTransitMode(mode)) {
            JsonNode transits = response.path("route").path("transits");
            return transits.isArray() && transits.isEmpty();
        }
        JsonNode v3Paths = response.path("route").path("paths");
        if (v3Paths.isArray()) {
            return v3Paths.isEmpty();
        }
        JsonNode v4Paths = response.path("data").path("paths");
        return v4Paths.isArray() && v4Paths.isEmpty();
    }

    private boolean isTransitMode(SegmentTransportMode mode) {
        return SegmentTransportMode.TRANSIT == mode
                || SegmentTransportMode.SUBWAY == mode
                || SegmentTransportMode.BUS == mode;
    }

    // ===== 实况天气 =====

    public Optional<RouteWeatherDTO> liveWeather(String cityAdcode) {
        if (!this.isAvailable() || StrUtil.isBlank(cityAdcode)) {
            return Optional.empty();
        }
        try {
            JsonNode response = this.getForObjectWithHealthyKey(
                    key -> this.buildWeatherUri(cityAdcode, key),
                    "天气查询"
            );
            Optional<RouteWeatherDTO> weather = RouteWeatherDTO.fromAmapLive(response);
            if (weather.isEmpty()) {
                this.logUnusableWeather(response);
            }
            return weather;
        } catch (RestClientException | IllegalArgumentException exception) {
            LOGGER.warn("高德天气查询失败，cityAdcode={}", cityAdcode, exception);
            return Optional.empty();
        }
    }

    private URI buildWeatherUri(String cityAdcode, String key) {
        return UriComponentsBuilder.fromUriString(this.amapWebProperties.getBaseUrl())
                .path("/v3/weather/weatherInfo")
                .queryParam("key", key)
                .queryParam("city", cityAdcode)
                .queryParam("extensions", EXTENSIONS_BASE)
                .build()
                .toUri();
    }

    private void logUnusableWeather(JsonNode response) {
        if (response == null) {
            LOGGER.warn("高德天气返回空响应");
            return;
        }
        LOGGER.warn(
                "高德天气无可用实况，status={}，info={}，infocode={}",
                response.path("status").asText(""),
                response.path("info").asText(""),
                response.path("infocode").asText("")
        );
    }

    // ===== 公共工具 =====

    private String join(List<String> values) {
        if (CollUtil.isEmpty(values)) {
            return "";
        }
        return String.join("|", values);
    }

    private String toLocation(GeoPointDTO point) {
        return point.longitudeGcj02().toPlainString() + "," + point.latitudeGcj02().toPlainString();
    }

    private String toPolygon(List<GeoPointDTO> polygon) {
        return polygon.stream()
                .map(this::toLocation)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private JsonNode getForObjectWithHealthyKey(Function<String, URI> uriFactory, String operationName) {
        int maxAttempts = Math.max(1, this.amapKeyPool.configuredKeyCount());
        RestClientException lastKeyFailure = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String key;
            try {
                key = this.amapKeyPool.acquireKey();
            } catch (IllegalStateException exception) {
                throw new RestClientException("没有可用高德 Web Key 执行" + operationName, exception);
            }
            JsonNode response = this.restTemplate.getForObject(uriFactory.apply(key), JsonNode.class);
            AmapKeyFailureClassifier.Classification classification = this.amapKeyFailureClassifier.classify(response);
            if (classification.shouldDisableKey()) {
                this.amapKeyPool.disableKey(key, classification);
                lastKeyFailure = new RestClientException("高德" + operationName + "命中不可用 Key：" + classification.reason());
                continue;
            }
            return response;
        }
        if (lastKeyFailure != null) {
            throw lastKeyFailure;
        }
        throw new RestClientException("没有可用高德 Web Key 执行" + operationName);
    }
}
