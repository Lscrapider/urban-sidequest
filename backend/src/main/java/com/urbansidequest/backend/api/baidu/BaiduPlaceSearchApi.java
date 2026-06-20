package com.urbansidequest.backend.api.baidu;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.urbansidequest.backend.api.support.FixedIntervalRateLimiter;
import com.urbansidequest.backend.config.BaiduMapProperties;
import com.urbansidequest.backend.domain.dto.BaiduPlaceSearchQueryDTO;
import com.urbansidequest.backend.domain.dto.GeoPointDTO;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BaiduPlaceSearchApi {

    private static final String SEARCH_TYPE_AROUND = "AROUND";

    private static final String SEARCH_TYPE_POLYGON = "POLYGON";

    private static final int BAIDU_COORD_TYPE_GCJ02 = 2;

    private static final int DETAIL_SCOPE = 2;

    private static final int MAX_PAGE_SIZE = 20;

    /**
     * 百度 Place 搜索接口按 3 QPS 控制。350ms 留出少量余量，避免边界抖动触发限流。
     */
    private static final long MIN_REQUEST_INTERVAL_NANOS = 350_000_000L;

    private final BaiduMapProperties baiduMapProperties;

    private final RestTemplate restTemplate;

    private final FixedIntervalRateLimiter rateLimiter = new FixedIntervalRateLimiter(MIN_REQUEST_INTERVAL_NANOS);

    public BaiduPlaceSearchApi(BaiduMapProperties baiduMapProperties, RestTemplateBuilder restTemplateBuilder) {
        this.baiduMapProperties = baiduMapProperties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(baiduMapProperties.getConnectTimeout())
                .readTimeout(baiduMapProperties.getReadTimeout())
                .build();
    }

    public JsonNode search(BaiduPlaceSearchQueryDTO query) {
        URI uri = switch (query.searchType()) {
            case SEARCH_TYPE_AROUND -> this.buildAroundUri(query);
            case SEARCH_TYPE_POLYGON -> this.buildPolygonUri(query);
            default -> throw new IllegalArgumentException("不支持的百度 POI 搜索类型：" + query.searchType());
        };
        this.rateLimiter.acquire();
        return this.restTemplate.getForObject(uri, JsonNode.class);
    }

    public boolean isAvailable() {
        return StrUtil.isNotBlank(this.baiduMapProperties.getAk());
    }

    private URI buildAroundUri(BaiduPlaceSearchQueryDTO query) {
        return this.baseUriBuilder("/place/v3/around", query)
                .queryParam("location", this.toBaiduLocation(query.center()))
                .queryParam("radius", query.radiusMeters())
                .build()
                .toUri();
    }

    private URI buildPolygonUri(BaiduPlaceSearchQueryDTO query) {
        return this.baseUriBuilder("/place/v3/polygon", query)
                .queryParam("bounds", this.toBaiduBounds(query.polygon()))
                .build()
                .toUri();
    }

    private UriComponentsBuilder baseUriBuilder(String path, BaiduPlaceSearchQueryDTO query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(this.baiduMapProperties.getBaseUrl())
                .path(path)
                .queryParam("ak", this.baiduMapProperties.getAk())
                .queryParam("output", "json")
                .queryParam("query", query.query())
                .queryParam("page_num", query.pageNum())
                .queryParam("page_size", Math.min(query.pageSize(), MAX_PAGE_SIZE))
                .queryParam("scope", DETAIL_SCOPE)
                .queryParam("coord_type", BAIDU_COORD_TYPE_GCJ02)
                .queryParam("ret_coordtype", "gcj02ll");
        if (StrUtil.isNotBlank(query.tag())) {
            builder.queryParam("tag", query.tag());
        }
        return builder;
    }

    private String toBaiduLocation(GeoPointDTO point) {
        return point.latitudeGcj02() + "," + point.longitudeGcj02();
    }

    /**
     * 百度 Place v3 的 polygon 接口使用 bounds 矩形范围，这里把项目内多边形转成外接矩形。
     */
    private String toBaiduBounds(List<GeoPointDTO> polygon) {
        if (CollUtil.isEmpty(polygon)) {
            return "";
        }
        BigDecimal minLng = polygon.stream()
                .map(GeoPointDTO::longitudeGcj02)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        BigDecimal maxLng = polygon.stream()
                .map(GeoPointDTO::longitudeGcj02)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        BigDecimal minLat = polygon.stream()
                .map(GeoPointDTO::latitudeGcj02)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        BigDecimal maxLat = polygon.stream()
                .map(GeoPointDTO::latitudeGcj02)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return minLat + "," + minLng + "," + maxLat + "," + maxLng;
    }
}
