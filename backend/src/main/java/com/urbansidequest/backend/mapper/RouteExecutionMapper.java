package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.po.RouteExecutionPO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RouteExecutionMapper extends BaseMapper<RouteExecutionPO> {

    @Insert("""
            INSERT INTO route_execution (
                user_id,
                candidate_set_id,
                route_code,
                execution_status,
                started_at
            )
            VALUES (
                #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{routeCode},
                'IN_PROGRESS',
                now()
            )
            """)
    int insertInProgress(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCode") String routeCode
    );

    @Update("""
            UPDATE route_execution
            SET execution_status = 'ABANDONED',
                updated_at = now()
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND execution_status = 'IN_PROGRESS'
            """)
    int abandonInProgressByUserId(@Param("userId") UUID userId);

    @Update("""
            UPDATE route_execution
            SET execution_status = 'COMPLETED',
                completed_at = now(),
                updated_at = now()
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND route_code = #{routeCode}
              AND execution_status = 'IN_PROGRESS'
            """)
    int completeInProgress(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCode") String routeCode
    );

    @Select("""
            SELECT
                id,
                user_id,
                candidate_set_id,
                route_code,
                execution_status,
                started_at,
                completed_at,
                map_snapshot_url,
                map_snapshot_object_key,
                created_at,
                updated_at
            FROM route_execution
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND execution_status = 'IN_PROGRESS'
            ORDER BY updated_at DESC
            LIMIT 1
            """)
    @Results(id = "RouteExecutionResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "execution_status", property = "executionStatus"),
            @Result(column = "started_at", property = "startedAt"),
            @Result(column = "completed_at", property = "completedAt"),
            @Result(column = "map_snapshot_url", property = "mapSnapshotUrl"),
            @Result(column = "map_snapshot_object_key", property = "mapSnapshotObjectKey"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    RouteExecutionPO selectInProgressByUserId(@Param("userId") UUID userId);

    @Select("""
            <script>
            SELECT
                id,
                user_id,
                candidate_set_id,
                route_code,
                execution_status,
                started_at,
                completed_at,
                map_snapshot_url,
                map_snapshot_object_key,
                created_at,
                updated_at
            FROM (
                SELECT
                    *,
                    row_number() OVER (
                        PARTITION BY candidate_set_id
                        ORDER BY
                            CASE execution_status
                                WHEN 'IN_PROGRESS' THEN 0
                                WHEN 'COMPLETED' THEN 1
                                ELSE 2
                            END,
                            updated_at DESC
                    ) AS row_num
                FROM route_execution
                WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
                  AND candidate_set_id IN
                  <foreach collection="candidateSetIds" item="candidateSetId" open="(" separator="," close=")">
                      #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
                  </foreach>
            ) ranked_execution
            WHERE row_num = 1
            </script>
            """)
    @Results(id = "RouteExecutionLatestResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "execution_status", property = "executionStatus"),
            @Result(column = "started_at", property = "startedAt"),
            @Result(column = "completed_at", property = "completedAt"),
            @Result(column = "map_snapshot_url", property = "mapSnapshotUrl"),
            @Result(column = "map_snapshot_object_key", property = "mapSnapshotObjectKey"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<RouteExecutionPO> selectLatestByCandidateSetIds(
            @Param("userId") UUID userId,
            @Param("candidateSetIds") List<UUID> candidateSetIds
    );

    @Select("""
            SELECT
                id,
                user_id,
                candidate_set_id,
                route_code,
                execution_status,
                started_at,
                completed_at,
                map_snapshot_url,
                map_snapshot_object_key,
                created_at,
                updated_at
            FROM route_execution
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            ORDER BY
                CASE execution_status
                    WHEN 'IN_PROGRESS' THEN 0
                    WHEN 'COMPLETED' THEN 1
                    ELSE 2
                END,
                updated_at DESC
            LIMIT 1
            """)
    @Results(id = "RouteExecutionRequestResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "execution_status", property = "executionStatus"),
            @Result(column = "started_at", property = "startedAt"),
            @Result(column = "completed_at", property = "completedAt"),
            @Result(column = "map_snapshot_url", property = "mapSnapshotUrl"),
            @Result(column = "map_snapshot_object_key", property = "mapSnapshotObjectKey"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    RouteExecutionPO selectLatestByCandidateSetId(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId
    );

    @Update("""
            UPDATE route_execution
            SET map_snapshot_url = #{mapSnapshotUrl},
                map_snapshot_object_key = #{mapSnapshotObjectKey},
                updated_at = now()
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND route_code = #{routeCode}
              AND execution_status = 'COMPLETED'
            """)
    int updateMapSnapshot(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCode") String routeCode,
            @Param("mapSnapshotUrl") String mapSnapshotUrl,
            @Param("mapSnapshotObjectKey") String mapSnapshotObjectKey
    );
}
