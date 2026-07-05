package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

public interface RouteGenerationHistoryMapper extends BaseMapper<RouteGenerationHistoryPO> {

    @Insert("""
            INSERT INTO route_generation_history (
                request_id,
                candidate_set_id,
                user_id,
                area_label,
                route_count,
                generation_status,
                generation_stage,
                generation_json,
                route_summaries_json
            )
            VALUES (
                #{history.requestId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{history.candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{history.userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{history.areaLabel},
                #{history.routeCount},
                #{history.generationStatus},
                #{history.generationStage},
                CAST(#{history.generationJson} AS JSONB),
                CAST(#{history.routeSummariesJson} AS JSONB)
            )
            ON CONFLICT (request_id) DO UPDATE SET
                candidate_set_id = EXCLUDED.candidate_set_id,
                user_id = EXCLUDED.user_id,
                area_label = EXCLUDED.area_label,
                route_count = EXCLUDED.route_count,
                generation_status = EXCLUDED.generation_status,
                generation_stage = EXCLUDED.generation_stage,
                generation_json = EXCLUDED.generation_json,
                route_summaries_json = EXCLUDED.route_summaries_json,
                updated_at = now()
            """)
    int upsertHistory(@Param("history") RouteGenerationHistoryPO history);

    @Select("""
            SELECT
                id,
                request_id,
                candidate_set_id,
                user_id,
                area_label,
                route_count,
                generation_status,
                generation_stage,
                generation_json::text AS generation_json,
                route_summaries_json::text AS route_summaries_json,
                created_at,
                updated_at
            FROM route_generation_history
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            ORDER BY created_at DESC
            LIMIT #{pageSize}
            OFFSET #{offset}
            """)
    @Results(id = "RouteGenerationHistoryResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "request_id", property = "requestId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "area_label", property = "areaLabel"),
            @Result(column = "route_count", property = "routeCount"),
            @Result(column = "generation_status", property = "generationStatus"),
            @Result(column = "generation_stage", property = "generationStage"),
            @Result(column = "generation_json", property = "generationJson"),
            @Result(column = "route_summaries_json", property = "routeSummariesJson"),
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
                request_id,
                candidate_set_id,
                user_id,
                area_label,
                route_count,
                generation_status,
                generation_stage,
                generation_json::text AS generation_json,
                route_summaries_json::text AS route_summaries_json,
                created_at,
                updated_at
            FROM route_generation_history
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND request_id = #{requestId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            """)
    @Results(id = "RouteGenerationHistoryDetailResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "request_id", property = "requestId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "area_label", property = "areaLabel"),
            @Result(column = "route_count", property = "routeCount"),
            @Result(column = "generation_status", property = "generationStatus"),
            @Result(column = "generation_stage", property = "generationStage"),
            @Result(column = "generation_json", property = "generationJson"),
            @Result(column = "route_summaries_json", property = "routeSummariesJson"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    RouteGenerationHistoryPO selectByUserAndRequestId(
            @Param("userId") UUID userId,
            @Param("requestId") UUID requestId
    );
}
