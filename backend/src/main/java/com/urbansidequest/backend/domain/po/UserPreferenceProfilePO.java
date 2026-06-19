package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("user_preference_profiles")
public class UserPreferenceProfilePO {

    @TableId("id")
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("distance_sensitivity")
    private BigDecimal distanceSensitivity;

    @TableField("budget_sensitivity")
    private BigDecimal budgetSensitivity;

    @TableField("transfer_sensitivity")
    private BigDecimal transferSensitivity;

    @TableField("hidden_gem_affinity")
    private BigDecimal hiddenGemAffinity;

    @TableField("profile_confidence")
    private BigDecimal profileConfidence;

    @TableField("questionnaire_version")
    private String questionnaireVersion;

    @TableField("completed_at")
    private Instant completedAt;

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

    public BigDecimal getDistanceSensitivity() {
        return this.distanceSensitivity;
    }

    public void setDistanceSensitivity(BigDecimal distanceSensitivity) {
        this.distanceSensitivity = distanceSensitivity;
    }

    public BigDecimal getBudgetSensitivity() {
        return this.budgetSensitivity;
    }

    public void setBudgetSensitivity(BigDecimal budgetSensitivity) {
        this.budgetSensitivity = budgetSensitivity;
    }

    public BigDecimal getTransferSensitivity() {
        return this.transferSensitivity;
    }

    public void setTransferSensitivity(BigDecimal transferSensitivity) {
        this.transferSensitivity = transferSensitivity;
    }

    public BigDecimal getHiddenGemAffinity() {
        return this.hiddenGemAffinity;
    }

    public void setHiddenGemAffinity(BigDecimal hiddenGemAffinity) {
        this.hiddenGemAffinity = hiddenGemAffinity;
    }

    public BigDecimal getProfileConfidence() {
        return this.profileConfidence;
    }

    public void setProfileConfidence(BigDecimal profileConfidence) {
        this.profileConfidence = profileConfidence;
    }

    public String getQuestionnaireVersion() {
        return this.questionnaireVersion;
    }

    public void setQuestionnaireVersion(String questionnaireVersion) {
        this.questionnaireVersion = questionnaireVersion;
    }

    public Instant getCompletedAt() {
        return this.completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
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
