package com.urbansidequest.backend.provider.user;

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
public class UserAvatarObjectStore {

    private final MinioClient minioClient;

    private final RouteShareStorageProperties properties;

    public UserAvatarObjectStore(
            @Qualifier("routePreferenceTrainingMinioClient") MinioClient minioClient,
            RouteShareStorageProperties properties
    ) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public StoredUserAvatar putAvatar(UUID userId, byte[] imageBytes, String contentType) {
        if (imageBytes.length > this.properties.getMaxImageSize().toBytes()) {
            throw new IllegalArgumentException("头像图片过大，请重新选择");
        }
        String objectKey = this.avatarObjectKey(userId, contentType);
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
            throw new IllegalStateException("用户头像写入 MinIO 失败: " + objectKey, exception);
        }
        return new StoredUserAvatar(objectKey, this.publicUrl(objectKey));
    }

    private String avatarObjectKey(UUID userId, String contentType) {
        String datePath = DateTimeFormatter.ofPattern("yyyy/MM/dd").format(OffsetDateTime.now());
        return "%s/avatar/%s/user=%s/avatar.%s".formatted(
                this.prefix(),
                datePath,
                userId,
                this.extension(contentType)
        );
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
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

    public record StoredUserAvatar(String objectKey, String imageUrl) {
    }
}
