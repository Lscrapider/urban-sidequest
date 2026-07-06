package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import java.time.Instant;
import java.util.UUID;

@TableName("route_execution")
public class RouteExecutionPO {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("candidate_set_id")
    private UUID candidateSetId;

    @TableField("route_code")
    private String routeCode;

    @TableField("execution_status")
    private RouteExecutionStatus executionStatus;

    @TableField("started_at")
    private Instant startedAt;

    @TableField("completed_at")
    private Instant completedAt;

    @TableField("map_snapshot_url")
    private String mapSnapshotUrl;

    @TableField("map_snapshot_object_key")
    private String mapSnapshotObjectKey;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return this.userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getCandidateSetId() {
        return this.candidateSetId;
    }

    public void setCandidateSetId(UUID candidateSetId) {
        this.candidateSetId = candidateSetId;
    }

    public String getRouteCode() {
        return this.routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public RouteExecutionStatus getExecutionStatus() {
        return this.executionStatus;
    }

    public void setExecutionStatus(RouteExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
    }

    public Instant getStartedAt() {
        return this.startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return this.completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getMapSnapshotUrl() {
        return this.mapSnapshotUrl;
    }

    public void setMapSnapshotUrl(String mapSnapshotUrl) {
        this.mapSnapshotUrl = mapSnapshotUrl;
    }

    public String getMapSnapshotObjectKey() {
        return this.mapSnapshotObjectKey;
    }

    public void setMapSnapshotObjectKey(String mapSnapshotObjectKey) {
        this.mapSnapshotObjectKey = mapSnapshotObjectKey;
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
