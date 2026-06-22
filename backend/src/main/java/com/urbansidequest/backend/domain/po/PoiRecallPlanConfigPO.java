package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@TableName("poi_recall_plan_config")
public class PoiRecallPlanConfigPO {

    @TableId("id")
    private UUID id;

    @TableField("plan_code")
    private String planCode;

    @TableField("plan_version")
    private String planVersion;

    @TableField("plan_type")
    private String planType;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("trigger_value")
    private String triggerValue;

    @TableField("tag_code")
    private String tagCode;

    @TableField(value = "amap_type_codes", select = false)
    private List<String> amapTypeCodes;

    @TableField(value = "amap_keywords", select = false)
    private List<String> amapKeywords;

    @TableField("role_hint")
    private String roleHint;

    @TableField("category_group_hint")
    private String categoryGroupHint;

    @TableField(value = "intent_tags", select = false)
    private List<String> intentTags;

    @TableField("priority")
    private Integer priority;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("reason_seed")
    private String reasonSeed;

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

    public String getPlanCode() {
        return this.planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanVersion() {
        return this.planVersion;
    }

    public void setPlanVersion(String planVersion) {
        this.planVersion = planVersion;
    }

    public String getPlanType() {
        return this.planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public String getTriggerType() {
        return this.triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerValue() {
        return this.triggerValue;
    }

    public void setTriggerValue(String triggerValue) {
        this.triggerValue = triggerValue;
    }

    public String getTagCode() {
        return this.tagCode;
    }

    public void setTagCode(String tagCode) {
        this.tagCode = tagCode;
    }

    public List<String> getAmapTypeCodes() {
        return this.amapTypeCodes;
    }

    public void setAmapTypeCodes(List<String> amapTypeCodes) {
        this.amapTypeCodes = amapTypeCodes;
    }

    public List<String> getAmapKeywords() {
        return this.amapKeywords;
    }

    public void setAmapKeywords(List<String> amapKeywords) {
        this.amapKeywords = amapKeywords;
    }

    public String getRoleHint() {
        return this.roleHint;
    }

    public void setRoleHint(String roleHint) {
        this.roleHint = roleHint;
    }

    public String getCategoryGroupHint() {
        return this.categoryGroupHint;
    }

    public void setCategoryGroupHint(String categoryGroupHint) {
        this.categoryGroupHint = categoryGroupHint;
    }

    public List<String> getIntentTags() {
        return this.intentTags;
    }

    public void setIntentTags(List<String> intentTags) {
        this.intentTags = intentTags;
    }

    public Integer getPriority() {
        return this.priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getReasonSeed() {
        return this.reasonSeed;
    }

    public void setReasonSeed(String reasonSeed) {
        this.reasonSeed = reasonSeed;
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
