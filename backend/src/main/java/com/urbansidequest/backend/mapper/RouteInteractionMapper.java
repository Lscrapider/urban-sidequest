package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.enums.RouteInteractionReaction;
import com.urbansidequest.backend.domain.po.RouteInteractionPO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

public interface RouteInteractionMapper extends BaseMapper<RouteInteractionPO> {

    @Select("""
            SELECT
                id,
                user_id,
                candidate_set_id,
                route_code,
                favorite,
                reaction,
                created_at,
                updated_at
            FROM route_interactions
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            ORDER BY updated_at DESC
            """)
    @Results(id = "RouteInteractionResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "favorite", property = "favorite"),
            @Result(column = "reaction", property = "reaction"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<RouteInteractionPO> selectByUserId(@Param("userId") UUID userId);

    @Insert("""
            INSERT INTO route_interactions (
                user_id,
                candidate_set_id,
                route_code,
                favorite,
                reaction
            )
            VALUES (
                #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{routeCode},
                #{favorite},
                #{reaction}
            )
            ON CONFLICT (user_id, candidate_set_id, route_code) DO UPDATE SET
                favorite = EXCLUDED.favorite,
                reaction = EXCLUDED.reaction,
                updated_at = now()
            """)
    int upsertInteraction(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCode") String routeCode,
            @Param("favorite") boolean favorite,
            @Param("reaction") RouteInteractionReaction reaction
    );
}
