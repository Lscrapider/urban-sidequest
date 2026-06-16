package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("route_segment_cost_cache")
public class RouteSegmentCostCachePO {

    @TableId("id")
    private UUID id;

    @TableField("origin_longitude_gcj02")
    private BigDecimal originLongitudeGcj02;

    @TableField("origin_latitude_gcj02")
    private BigDecimal originLatitudeGcj02;

    @TableField("destination_longitude_gcj02")
    private BigDecimal destinationLongitudeGcj02;

    @TableField("destination_latitude_gcj02")
    private BigDecimal destinationLatitudeGcj02;

    @TableField("mode")
    private String mode;

    @TableField("distance_meters")
    private Integer distanceMeters;

    @TableField("duration_seconds")
    private Integer durationSeconds;

    @TableField("walk_distance_meters")
    private Integer walkDistanceMeters;

    @TableField("transfer_count")
    private Integer transferCount;

    @TableField("raw_payload")
    private String rawPayload;

    @TableField("fetched_at")
    private Instant fetchedAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
