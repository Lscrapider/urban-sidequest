package com.urbansidequest.backend.provider.route.training;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.config.RoutePreferenceTrainingStorageProperties;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceJudgmentIngestPayload;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceTrainingIngestPayload;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.springframework.stereotype.Component;

@Component
public class RoutePreferenceTrainingObjectStore {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final String GZIP_JSON_CONTENT_TYPE = "application/gzip";

    private final MinioClient minioClient;

    private final ObjectMapper objectMapper;

    private final RoutePreferenceTrainingStorageProperties properties;

    public RoutePreferenceTrainingObjectStore(
            MinioClient minioClient,
            ObjectMapper objectMapper,
            RoutePreferenceTrainingStorageProperties properties
    ) {
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void writeCandidateSet(RoutePreferenceTrainingIngestPayload payload) {
        if (!this.properties.isWriteEnabled()) {
            return;
        }
        String objectKey = this.candidateSetObjectKey(payload.candidateSetId());
        this.putGzipJson(objectKey, payload);
        this.putJson(this.candidateSetReadyObjectKey(payload.candidateSetId()), this.readyMarker(payload, objectKey));
    }

    public void writeJudgment(RoutePreferenceJudgmentIngestPayload payload) {
        if (!this.properties.isWriteEnabled()) {
            return;
        }
        this.putGzipJson(this.judgmentObjectKey(payload.candidateSetId(), payload.judgmentId()), payload);
    }

    private Map<String, Object> readyMarker(RoutePreferenceTrainingIngestPayload payload, String objectKey) {
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("candidateSetId", payload.candidateSetId());
        marker.put("requestId", payload.requestId());
        marker.put("objectKey", objectKey);
        marker.put("createdAt", OffsetDateTime.now());
        return marker;
    }

    private void putJson(String objectKey, Object payload) {
        try {
            byte[] data = this.objectMapper.writeValueAsBytes(payload);
            this.putObject(objectKey, data, JSON_CONTENT_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线偏好训练数据 ready marker 序列化失败", exception);
        }
    }

    private void putGzipJson(String objectKey, Object payload) {
        try {
            byte[] json = this.objectMapper.writeValueAsBytes(payload);
            this.putObject(objectKey, this.gzip(json), GZIP_JSON_CONTENT_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("路线偏好训练数据对象序列化失败", exception);
        }
    }

    private void putObject(String objectKey, byte[] data, String contentType) {
        try {
            this.minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(this.properties.getBucket())
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("路线偏好训练数据写入 MinIO 失败: " + objectKey, exception);
        }
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(data);
        }
        return outputStream.toByteArray();
    }

    private String candidateSetObjectKey(UUID candidateSetId) {
        return "%s/ingest/candidate_sets/shard=%s/%s.json.gz".formatted(
                this.prefix(),
                this.shard(candidateSetId),
                candidateSetId
        );
    }

    private String candidateSetReadyObjectKey(UUID candidateSetId) {
        return "%s/ingest/candidate_sets_ready/shard=%s/%s.json".formatted(
                this.prefix(),
                this.shard(candidateSetId),
                candidateSetId
        );
    }

    private String judgmentObjectKey(UUID candidateSetId, UUID judgmentId) {
        return "%s/ingest/judgments/shard=%s/%s/%s.json.gz".formatted(
                this.prefix(),
                this.shard(candidateSetId),
                candidateSetId,
                judgmentId
        );
    }

    private String shard(UUID candidateSetId) {
        return candidateSetId.toString().replace("-", "").substring(0, 2).toLowerCase();
    }

    private String prefix() {
        String prefix = this.properties.getPrefix();
        if (prefix == null || prefix.isBlank()) {
            return "route-preference";
        }
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
