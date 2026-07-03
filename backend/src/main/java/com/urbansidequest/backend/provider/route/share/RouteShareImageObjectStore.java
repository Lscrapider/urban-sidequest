package com.urbansidequest.backend.provider.route.share;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.config.RouteShareStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class RouteShareImageObjectStore {

    private final MinioClient minioClient;

    private final RouteShareStorageProperties properties;

    public RouteShareImageObjectStore(
            @Qualifier("routePreferenceTrainingMinioClient") MinioClient minioClient,
            RouteShareStorageProperties properties
    ) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public StoredRouteShareImage putShareImage(UUID userId, UUID requestId, String routeCode, byte[] imageBytes, String contentType) {
        if (imageBytes.length > this.properties.getMaxImageSize().toBytes()) {
            throw new IllegalArgumentException("分享图片过大，请重新生成后再分享");
        }
        String objectKey = this.shareImageObjectKey(userId, requestId, routeCode);
        try {
            this.minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(this.properties.getBucket())
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(imageBytes), imageBytes.length, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("分享路线图片写入 MinIO 失败: " + objectKey, exception);
        }
        return new StoredRouteShareImage(objectKey, this.publicUrl(objectKey));
    }

    private String shareImageObjectKey(UUID userId, UUID requestId, String routeCode) {
        String datePath = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(OffsetDateTime.now());
        return "%s/%s/user=%s/%s-%s.jpg".formatted(
                this.prefix(),
                datePath,
                userId,
                requestId,
                routeCode
        );
    }

    private String publicUrl(String objectKey) {
        return "/%s/%s".formatted(
                this.properties.getBucket(),
                objectKey
        );
    }

    private String prefix() {
        String prefix = this.properties.getPrefix();
        if (StrUtil.isBlank(prefix)) {
            return "route-share";
        }
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    public record StoredRouteShareImage(String objectKey, String imageUrl) {
    }
}
