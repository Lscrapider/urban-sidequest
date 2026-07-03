package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("users")
public class UserPO {

    @TableId("id")
    private UUID id;

    private String phone;

    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    private String status;

    @TableField("completed_route_count")
    private Integer completedRouteCount;

    @TableField("travel_distance_meters")
    private Long travelDistanceMeters;

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

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNickname() {
        return this.nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return this.avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCompletedRouteCount() {
        return this.completedRouteCount;
    }

    public void setCompletedRouteCount(Integer completedRouteCount) {
        this.completedRouteCount = completedRouteCount;
    }

    public Long getTravelDistanceMeters() {
        return this.travelDistanceMeters;
    }

    public void setTravelDistanceMeters(Long travelDistanceMeters) {
        this.travelDistanceMeters = travelDistanceMeters;
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
