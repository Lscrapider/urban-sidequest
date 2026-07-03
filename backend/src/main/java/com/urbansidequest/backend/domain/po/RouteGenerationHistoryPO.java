package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import java.time.Instant;
import java.util.UUID;

@TableName("route_generation_history")
public class RouteGenerationHistoryPO {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("request_id")
    private UUID requestId;

    @TableField("candidate_set_id")
    private UUID candidateSetId;

    @TableField("user_id")
    private UUID userId;

    @TableField("area_label")
    private String areaLabel;

    @TableField("route_count")
    private Integer routeCount;

    @TableField("generation_status")
    private RouteRequestStatus generationStatus;

    @TableField("generation_stage")
    private String generationStage;

    @TableField("generation_json")
    private String generationJson;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public static RouteGenerationHistoryPO fromRouteGeneration(RouteGenerationVO routeGeneration, ObjectMapper objectMapper) {
        RouteGenerationHistoryPO po = new RouteGenerationHistoryPO();
        po.setRequestId(routeGeneration.requestId());
        po.setCandidateSetId(routeGeneration.candidateSetId());
        po.setUserId(routeGeneration.userId());
        po.setAreaLabel(routeGeneration.area().areaLabel());
        po.setRouteCount(routeGeneration.routes().size());
        po.setGenerationStatus(routeGeneration.status());
        po.setGenerationStage(routeGeneration.generationStage());
        po.setGenerationJson(writeJson(objectMapper, routeGeneration));
        return po;
    }

    public RouteGenerationVO toRouteGenerationVO(ObjectMapper objectMapper) {
        RouteGenerationVO routeGeneration = readJson(objectMapper, this.generationJson, RouteGenerationVO.class);
        return new RouteGenerationVO(
                routeGeneration.requestId(),
                routeGeneration.candidateSetId(),
                routeGeneration.userId(),
                this.generationStatus == null ? routeGeneration.status() : this.generationStatus,
                routeGeneration.area(),
                routeGeneration.routes(),
                routeGeneration.warnings(),
                this.generationStage == null ? routeGeneration.generationStage() : this.generationStage,
                null,
                RouteExecutionStatus.GENERATED
        );
    }

    private static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线历史快照序列化失败", exception);
        }
    }

    private static <T> T readJson(ObjectMapper objectMapper, String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线历史快照反序列化失败", exception);
        }
    }

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRequestId() {
        return this.requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getCandidateSetId() {
        return this.candidateSetId;
    }

    public void setCandidateSetId(UUID candidateSetId) {
        this.candidateSetId = candidateSetId;
    }

    public UUID getUserId() {
        return this.userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAreaLabel() {
        return this.areaLabel;
    }

    public void setAreaLabel(String areaLabel) {
        this.areaLabel = areaLabel;
    }

    public Integer getRouteCount() {
        return this.routeCount;
    }

    public void setRouteCount(Integer routeCount) {
        this.routeCount = routeCount;
    }

    public RouteRequestStatus getGenerationStatus() {
        return this.generationStatus;
    }

    public void setGenerationStatus(RouteRequestStatus generationStatus) {
        this.generationStatus = generationStatus == null ? RouteRequestStatus.SUCCESS : generationStatus;
    }

    public String getGenerationStage() {
        return this.generationStage;
    }

    public void setGenerationStage(String generationStage) {
        this.generationStage = generationStage;
    }

    public String getGenerationJson() {
        return this.generationJson;
    }

    public void setGenerationJson(String generationJson) {
        this.generationJson = generationJson;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
