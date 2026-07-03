package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.urbansidequest.backend.domain.enums.RoutePreferenceFeedbackLabel;
import java.time.Instant;
import java.util.UUID;

@TableName("route_preference_feedbacks")
public class RoutePreferenceFeedbackPO {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("candidate_set_id")
    private UUID candidateSetId;

    @TableField("route_code")
    private String routeCode;

    @TableField("feedback_label")
    private RoutePreferenceFeedbackLabel feedbackLabel;

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

    public RoutePreferenceFeedbackLabel getFeedbackLabel() {
        return this.feedbackLabel;
    }

    public void setFeedbackLabel(RoutePreferenceFeedbackLabel feedbackLabel) {
        this.feedbackLabel = feedbackLabel;
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
