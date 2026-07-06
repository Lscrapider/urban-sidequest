package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.po.RouteSharePO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

public interface RouteShareMapper extends BaseMapper<RouteSharePO> {

    @Select("""
            SELECT
                id,
                user_id,
                candidate_set_id,
                route_code,
                share_text,
                image_url,
                image_object_key,
                created_at,
                updated_at
            FROM route_shares
            ORDER BY created_at DESC
            LIMIT #{pageSize}
            """)
    @Results(id = "RouteShareResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "share_text", property = "shareText"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "image_object_key", property = "imageObjectKey"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<RouteSharePO> selectLatest(@Param("pageSize") int pageSize);

    @Select("""
            SELECT
                id,
                user_id,
                candidate_set_id,
                route_code,
                share_text,
                image_url,
                image_object_key,
                created_at,
                updated_at
            FROM route_shares
            WHERE id = #{shareId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            """)
    @Results(id = "RouteShareResultById", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "share_text", property = "shareText"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "image_object_key", property = "imageObjectKey"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    RouteSharePO selectByShareId(@Param("shareId") UUID shareId);

    @Insert("""
            INSERT INTO route_shares (
                user_id,
                candidate_set_id,
                route_code,
                share_text,
                image_url,
                image_object_key
            )
            VALUES (
                #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{routeCode},
                #{shareText},
                #{imageUrl},
                #{imageObjectKey}
            )
            ON CONFLICT (user_id, candidate_set_id, route_code) DO UPDATE SET
                share_text = EXCLUDED.share_text,
                image_url = EXCLUDED.image_url,
                image_object_key = EXCLUDED.image_object_key,
                updated_at = now()
            """)
    int upsertShare(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCode") String routeCode,
            @Param("shareText") String shareText,
            @Param("imageUrl") String imageUrl,
            @Param("imageObjectKey") String imageObjectKey
    );

    @Select("""
            SELECT
                id,
                user_id,
                candidate_set_id,
                route_code,
                share_text,
                image_url,
                image_object_key,
                created_at,
                updated_at
            FROM route_shares
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
              AND route_code = #{routeCode}
            """)
    @Results(id = "RouteShareResultByUserRoute", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "route_code", property = "routeCode"),
            @Result(column = "share_text", property = "shareText"),
            @Result(column = "image_url", property = "imageUrl"),
            @Result(column = "image_object_key", property = "imageObjectKey"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    RouteSharePO selectByUserRoute(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCode") String routeCode
    );
}
