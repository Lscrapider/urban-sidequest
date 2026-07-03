package com.urbansidequest.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "route.share.storage")
public class RouteShareStorageProperties {

    private String bucket;

    private String prefix = "route-share";

    private DataSize maxImageSize = DataSize.ofMegabytes(2);

    public String getBucket() {
        return this.bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public DataSize getMaxImageSize() {
        return this.maxImageSize;
    }

    public void setMaxImageSize(DataSize maxImageSize) {
        this.maxImageSize = maxImageSize == null ? DataSize.ofMegabytes(2) : maxImageSize;
    }
}
