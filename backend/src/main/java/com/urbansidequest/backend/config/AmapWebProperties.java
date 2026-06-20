package com.urbansidequest.backend.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "amap.web")
public class AmapWebProperties {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(8);

    /**
     * 单 key 配置，向后兼容；多 key 场景用 {@link #keys}。
     */
    private String key;

    /**
     * 多个高德 Web Key；为空时回退到单 {@link #key}。聚合 QPS = key 数 × {@link #keyQps}。
     */
    private List<String> keys = new ArrayList<>();

    /**
     * 每个 key 的限速 QPS（高德 Web 服务单 key 上限为 3，默认 2.8 留余量）。聚合可用 QPS = key 数 × keyQps。
     */
    private double keyQps = 2.8;

    private String baseUrl = "https://restapi.amap.com";

    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    private Duration readTimeout = DEFAULT_READ_TIMEOUT;

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<String> getKeys() {
        return this.keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys == null ? new ArrayList<>() : keys;
    }

    public double getKeyQps() {
        return this.keyQps;
    }

    public void setKeyQps(double keyQps) {
        this.keyQps = keyQps;
    }

    /**
     * 实际可用的 key 列表：优先用 {@link #keys}，为空则回退到单 {@link #key}，均去除空白项。
     */
    public List<String> effectiveKeys() {
        if (CollUtil.isNotEmpty(this.keys)) {
            return this.keys.stream().filter(StrUtil::isNotBlank).distinct().toList();
        }
        return StrUtil.isBlank(this.key) ? List.of() : List.of(this.key);
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return this.connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout == null ? DEFAULT_CONNECT_TIMEOUT : connectTimeout;
    }

    public Duration getReadTimeout() {
        return this.readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout == null ? DEFAULT_READ_TIMEOUT : readTimeout;
    }
}
