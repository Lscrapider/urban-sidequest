package com.urbansidequest.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "amap.web")
public class AmapWebProperties {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(8);

    private String key;

    private String baseUrl = "https://restapi.amap.com";

    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    private Duration readTimeout = DEFAULT_READ_TIMEOUT;

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
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
