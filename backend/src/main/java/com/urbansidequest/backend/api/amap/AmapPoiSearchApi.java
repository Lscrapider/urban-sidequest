package com.urbansidequest.backend.api.amap;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.config.AmapWebProperties;
import com.urbansidequest.backend.domain.dto.AmapPoiSearchQueryDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import java.net.URI;
import java.util.List;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AmapPoiSearchApi {

    private static final String SEARCH_TYPE_AROUND = "AROUND";

    private static final String SEARCH_TYPE_POLYGON = "POLYGON";

    private static final String SHOW_FIELDS = "business,photos";

    private final AmapWebProperties amapWebProperties;

    private final RestTemplate restTemplate;

    public AmapPoiSearchApi(AmapWebProperties amapWebProperties, RestTemplateBuilder restTemplateBuilder) {
        this.amapWebProperties = amapWebProperties;
        this.restTemplate = restTemplateBuilder.build();
    }

    public JsonNode search(AmapPoiSearchQueryDTO query) {
        URI uri = switch (query.searchType()) {
            case SEARCH_TYPE_AROUND -> this.buildAroundUri(query);
            case SEARCH_TYPE_POLYGON -> this.buildPolygonUri(query);
            default -> throw new IllegalArgumentException("不支持的高德 POI 搜索类型：" + query.searchType());
        };
        return this.restTemplate.getForObject(uri, JsonNode.class);
    }

    public boolean isAvailable() {
        return StrUtil.isNotBlank(this.amapWebProperties.getKey());
    }

    private URI buildAroundUri(AmapPoiSearchQueryDTO query) {
        return this.baseUriBuilder("/v5/place/around")
                .queryParam("location", this.toLocation(query.center()))
                .queryParam("radius", query.radiusMeters())
                .queryParam("types", this.join(query.types()))
                .queryParam("keywords", this.join(query.keywords()))
                .queryParam("page_num", query.pageNum())
                .queryParam("page_size", query.pageSize())
                .build()
                .toUri();
    }

    private URI buildPolygonUri(AmapPoiSearchQueryDTO query) {
        return this.baseUriBuilder("/v5/place/polygon")
                .queryParam("polygon", this.toPolygon(query.polygon()))
                .queryParam("types", this.join(query.types()))
                .queryParam("keywords", this.join(query.keywords()))
                .queryParam("page_num", query.pageNum())
                .queryParam("page_size", query.pageSize())
                .build()
                .toUri();
    }

    private UriComponentsBuilder baseUriBuilder(String path) {
        return UriComponentsBuilder.fromUriString(this.amapWebProperties.getBaseUrl())
                .path(path)
                .queryParam("key", this.amapWebProperties.getKey())
                .queryParam("show_fields", SHOW_FIELDS);
    }

    private String join(List<String> values) {
        if (CollUtil.isEmpty(values)) {
            return "";
        }
        return String.join("|", values);
    }

    private String toLocation(GeoPointDTO point) {
        return point.longitudeGcj02() + "," + point.latitudeGcj02();
    }

    private String toPolygon(List<GeoPointDTO> polygon) {
        return polygon.stream()
                .map(this::toLocation)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }
}
