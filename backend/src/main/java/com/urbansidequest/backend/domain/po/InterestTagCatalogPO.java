package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@TableName("interest_tag_catalog")
public class InterestTagCatalogPO {

    @TableId("id")
    private UUID id;

    @TableField("tag_code")
    private String tagCode;

    @TableField("display_name")
    private String displayName;

    @TableField("parent_tag_code")
    private String parentTagCode;

    @TableField("tag_level")
    private String tagLevel;

    @TableField("selectable")
    private Boolean selectable;

    @TableField("max_sibling_selected")
    private Integer maxSiblingSelected;

    @TableField(value = "rollup_tag_codes", select = false)
    private List<String> rollupTagCodes;

    @TableField(value = "amap_type_codes", select = false)
    private List<String> amapTypeCodes;

    @TableField(value = "amap_keywords", select = false)
    private List<String> amapKeywords;

    @TableField("category_group")
    private String categoryGroup;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("catalog_version")
    private String catalogVersion;

    private Boolean enabled;

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

    public String getTagCode() {
        return this.tagCode;
    }

    public void setTagCode(String tagCode) {
        this.tagCode = tagCode;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getParentTagCode() {
        return this.parentTagCode;
    }

    public void setParentTagCode(String parentTagCode) {
        this.parentTagCode = parentTagCode;
    }

    public String getTagLevel() {
        return this.tagLevel;
    }

    public void setTagLevel(String tagLevel) {
        this.tagLevel = tagLevel;
    }

    public Boolean getSelectable() {
        return this.selectable;
    }

    public void setSelectable(Boolean selectable) {
        this.selectable = selectable;
    }

    public Integer getMaxSiblingSelected() {
        return this.maxSiblingSelected;
    }

    public void setMaxSiblingSelected(Integer maxSiblingSelected) {
        this.maxSiblingSelected = maxSiblingSelected;
    }

    public List<String> getRollupTagCodes() {
        return this.rollupTagCodes;
    }

    public void setRollupTagCodes(List<String> rollupTagCodes) {
        this.rollupTagCodes = rollupTagCodes;
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

    public String getCategoryGroup() {
        return this.categoryGroup;
    }

    public void setCategoryGroup(String categoryGroup) {
        this.categoryGroup = categoryGroup;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getCatalogVersion() {
        return this.catalogVersion;
    }

    public void setCatalogVersion(String catalogVersion) {
        this.catalogVersion = catalogVersion;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
