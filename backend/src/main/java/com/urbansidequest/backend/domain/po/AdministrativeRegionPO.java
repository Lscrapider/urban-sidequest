package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;

@TableName("administrative_regions")
public class AdministrativeRegionPO {

    @TableId("adcode")
    private String adcode;

    @TableField("parent_adcode")
    private String parentAdcode;

    @TableField("name")
    private String name;

    @TableField("level")
    private String level;

    @TableField("longitude_gcj02")
    private BigDecimal longitudeGcj02;

    @TableField("latitude_gcj02")
    private BigDecimal latitudeGcj02;

    @TableField("selectable")
    private Boolean selectable;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("children_loaded")
    private Boolean childrenLoaded;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public String getAdcode() {
        return this.adcode;
    }

    public void setAdcode(String adcode) {
        this.adcode = adcode;
    }

    public String getParentAdcode() {
        return this.parentAdcode;
    }

    public void setParentAdcode(String parentAdcode) {
        this.parentAdcode = parentAdcode;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLevel() {
        return this.level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public BigDecimal getLongitudeGcj02() {
        return this.longitudeGcj02;
    }

    public void setLongitudeGcj02(BigDecimal longitudeGcj02) {
        this.longitudeGcj02 = longitudeGcj02;
    }

    public BigDecimal getLatitudeGcj02() {
        return this.latitudeGcj02;
    }

    public void setLatitudeGcj02(BigDecimal latitudeGcj02) {
        this.latitudeGcj02 = latitudeGcj02;
    }

    public Boolean getSelectable() {
        return this.selectable;
    }

    public void setSelectable(Boolean selectable) {
        this.selectable = selectable;
    }

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getChildrenLoaded() {
        return this.childrenLoaded;
    }

    public void setChildrenLoaded(Boolean childrenLoaded) {
        this.childrenLoaded = childrenLoaded;
    }

    public Integer getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
