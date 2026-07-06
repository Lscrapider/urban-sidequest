package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RouteGenerationHistoryMapper extends BaseMapper<RouteGenerationHistoryPO> {

    @Insert("""
            INSERT INTO route_generation_history (
                candidate_set_id,
                user_id,
                area_label,
                route_count,
                generation_status,
                generation_stage,
                route_code,
                route_index,
                route_title,
                city_name,
                total_duration_minutes,
                total_distance_meters,
                risk_level,
                stop_count,
                generation_json
            )
            VALUES (
                #{history.candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{history.userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{history.areaLabel},
                #{history.routeCount},
                #{history.generationStatus},
                #{history.generationStage},
                #{history.routeCode},
                #{history.routeIndex},
                #{history.routeTitle},
                #{history.cityName},
                #{history.totalDurationMinutes},
                #{history.totalDistanceMeters},
                #{history.riskLevel},
                #{history.stopCount},
                CAST(#{history.generationJson} AS JSONB)
            )
            ON CONFLICT (candidate_set_id, route_code) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                area_label = EXCLUDED.area_label,
                route_count = EXCLUDED.route_count,
                generation_status = EXCLUDED.generation_status,
                generation_stage = EXCLUDED.generation_stage,
                route_index = EXCLUDED.route_index,
                route_title = EXCLUDED.route_title,
                city_name = EXCLUDED.city_name,
                total_duration_minutes = EXCLUDED.total_duration_minutes,
                total_distance_meters = EXCLUDED.total_distance_meters,
                risk_level = EXCLUDED.risk_level,
                stop_count = EXCLUDED.stop_count,
                generation_json = EXCLUDED.generation_json,
                updated_at = now()
            """)
    int upsertRoute(@Param("history") RouteGenerationHistoryPO history);

    @Update("""
            UPDATE route_generation_history
            SET user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                area_label = #{areaLabel},
                route_count = #{routeCount},
                generation_status = #{generationStatus},
                generation_stage = #{generationStage},
                updated_at = now()
            WHERE candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            """)
    int updateGenerationState(
            @Param("candidateSetId") UUID candidateSetId,
            @Param("userId") UUID userId,
            @Param("areaLabel") String areaLabel,
            @Param("routeCount") int routeCount,
            @Param("generationStatus") RouteRequestStatus generationStatus,
            @Param("generationStage") String generationStage
    );

    @Delete("""
            <script>
            DELETE FROM route_generation_history
            WHERE candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND route_code NOT IN
              <foreach collection="routeCodes" item="routeCode" open="(" separator="," close=")">
                  #{routeCode}
              </foreach>
            </script>
            """)
    int deleteRoutesNotIn(
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCodes") List<String> routeCodes
    );

    @Select("""
            <script>
            WITH candidate_page AS (
                SELECT
                    candidate_set_id,
                    max(created_at) AS latest_created_at
                FROM route_generation_history
                WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
                GROUP BY candidate_set_id
                ORDER BY latest_created_at DESC
                LIMIT #{pageSize}
                OFFSET #{offset}
            )
            SELECT
                history.id,
                history.candidate_set_id,
                history.user_id,
                history.area_label,
                history.route_count,
                history.generation_status,
                history.generation_stage,
                history.route_code,
                history.route_index,
                history.route_title,
                history.city_name,
                history.total_duration_minutes,
                history.total_distance_meters,
                history.risk_level,
                history.stop_count,
                history.generation_json::text AS generation_json,
                history.created_at,
                history.updated_at
            FROM route_generation_history
                history
                INNER JOIN candidate_page
                    ON candidate_page.candidate_set_id = history.candidate_set_id
            WHERE history.user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            ORDER BY candidate_page.latest_created_at DESC, history.route_index ASC, history.created_at ASC
            </script>
            """)
    @Results(id = "RouteGenerationHistoryResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "area_label", property = "areaLabel"),
            @Result(column = "route_count", property = "routeCount"),
            @Result(column = "generation_status", property = "generationStatus"),
            @Result(column = "generation_stage", property = "generationStage"),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "route_index", property = "routeIndex"),
            @Result(column = "route_title", property = "routeTitle"),
            @Result(column = "city_name", property = "cityName"),
            @Result(column = "total_duration_minutes", property = "totalDurationMinutes"),
            @Result(column = "total_distance_meters", property = "totalDistanceMeters"),
            @Result(column = "risk_level", property = "riskLevel"),
            @Result(column = "stop_count", property = "stopCount"),
            @Result(column = "generation_json", property = "generationJson"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<RouteGenerationHistoryPO> selectByUserId(
            @Param("userId") UUID userId,
            @Param("pageSize") int pageSize,
            @Param("offset") int offset
    );

    @Select("""
            SELECT
                id,
                candidate_set_id,
                user_id,
                area_label,
                route_count,
                generation_status,
                generation_stage,
                route_code,
                route_index,
                route_title,
                city_name,
                total_duration_minutes,
                total_distance_meters,
                risk_level,
                stop_count,
                generation_json::text AS generation_json,
                created_at,
                updated_at
            FROM route_generation_history
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            ORDER BY route_index ASC, created_at ASC
            """)
    @Results(id = "RouteGenerationHistoryDetailResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "area_label", property = "areaLabel"),
            @Result(column = "route_count", property = "routeCount"),
            @Result(column = "generation_status", property = "generationStatus"),
            @Result(column = "generation_stage", property = "generationStage"),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "route_index", property = "routeIndex"),
            @Result(column = "route_title", property = "routeTitle"),
            @Result(column = "city_name", property = "cityName"),
            @Result(column = "total_duration_minutes", property = "totalDurationMinutes"),
            @Result(column = "total_distance_meters", property = "totalDistanceMeters"),
            @Result(column = "risk_level", property = "riskLevel"),
            @Result(column = "stop_count", property = "stopCount"),
            @Result(column = "generation_json", property = "generationJson"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<RouteGenerationHistoryPO> selectByUserAndCandidateSetId(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId
    );
}
