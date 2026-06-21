package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.domain.po.RouteSegmentCostCachePO;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RouteSegmentCostCacheMapper extends BaseMapper<RouteSegmentCostCachePO> {

    @Select("""
            SELECT raw_payload::text
            FROM route_segment_cost_cache
            WHERE origin_longitude_gcj02 = #{originLongitude}
              AND origin_latitude_gcj02 = #{originLatitude}
              AND destination_longitude_gcj02 = #{destinationLongitude}
              AND destination_latitude_gcj02 = #{destinationLatitude}
              AND mode = #{mode}
              AND raw_payload IS NOT NULL
            ORDER BY fetched_at DESC
            LIMIT 1
            """)
    String findLatestRawPayload(
            @Param("originLongitude") BigDecimal originLongitude,
            @Param("originLatitude") BigDecimal originLatitude,
            @Param("destinationLongitude") BigDecimal destinationLongitude,
            @Param("destinationLatitude") BigDecimal destinationLatitude,
            @Param("mode") String mode
    );

    @Select("""
            SELECT raw_payload IS NULL
            FROM route_segment_cost_cache
            WHERE origin_longitude_gcj02 = #{originLongitude}
              AND origin_latitude_gcj02 = #{originLatitude}
              AND destination_longitude_gcj02 = #{destinationLongitude}
              AND destination_latitude_gcj02 = #{destinationLatitude}
              AND mode = #{mode}
            ORDER BY fetched_at DESC
            LIMIT 1
            """)
    Boolean isLatestNoRoute(
            @Param("originLongitude") BigDecimal originLongitude,
            @Param("originLatitude") BigDecimal originLatitude,
            @Param("destinationLongitude") BigDecimal destinationLongitude,
            @Param("destinationLatitude") BigDecimal destinationLatitude,
            @Param("mode") String mode
    );

    @Insert("""
            INSERT INTO route_segment_cost_cache (
                origin_longitude_gcj02,
                origin_latitude_gcj02,
                destination_longitude_gcj02,
                destination_latitude_gcj02,
                mode,
                distance_meters,
                duration_seconds,
                raw_payload,
                fetched_at
            )
            VALUES (
                #{originLongitude},
                #{originLatitude},
                #{destinationLongitude},
                #{destinationLatitude},
                #{mode},
                #{distanceMeters},
                #{durationSeconds},
                CAST(#{rawPayload} AS JSONB),
                now()
            )
            """)
    int insertRawPayload(
            @Param("originLongitude") BigDecimal originLongitude,
            @Param("originLatitude") BigDecimal originLatitude,
            @Param("destinationLongitude") BigDecimal destinationLongitude,
            @Param("destinationLatitude") BigDecimal destinationLatitude,
            @Param("mode") String mode,
            @Param("distanceMeters") int distanceMeters,
            @Param("durationSeconds") int durationSeconds,
            @Param("rawPayload") String rawPayload
    );

    @Insert("""
            INSERT INTO route_segment_cost_cache (
                origin_longitude_gcj02,
                origin_latitude_gcj02,
                destination_longitude_gcj02,
                destination_latitude_gcj02,
                mode,
                distance_meters,
                duration_seconds,
                raw_payload,
                fetched_at
            )
            VALUES (
                #{originLongitude},
                #{originLatitude},
                #{destinationLongitude},
                #{destinationLatitude},
                #{mode},
                0,
                0,
                NULL,
                now()
            )
            """)
    int insertNoRoute(
            @Param("originLongitude") BigDecimal originLongitude,
            @Param("originLatitude") BigDecimal originLatitude,
            @Param("destinationLongitude") BigDecimal destinationLongitude,
            @Param("destinationLatitude") BigDecimal destinationLatitude,
            @Param("mode") String mode
    );
}
