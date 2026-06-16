package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.domain.po.AmapPoiSearchCachePO;
import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AmapPoiSearchCacheMapper extends BaseMapper<AmapPoiSearchCachePO> {

    @Select("""
            SELECT response_json::text
            FROM amap_poi_search_cache
            WHERE search_type = #{searchType}
              AND area_hash = #{areaHash}
              AND types_hash = #{typesHash}
              AND keywords_hash = #{keywordsHash}
              AND page_num = #{pageNum}
              AND page_size = #{pageSize}
              AND expires_at > now()
            LIMIT 1
            """)
    String findValidResponseJson(
            @Param("searchType") String searchType,
            @Param("areaHash") String areaHash,
            @Param("typesHash") String typesHash,
            @Param("keywordsHash") String keywordsHash,
            @Param("pageNum") int pageNum,
            @Param("pageSize") int pageSize
    );

    @Insert("""
            INSERT INTO amap_poi_search_cache (
                search_type,
                area_hash,
                types_hash,
                keywords_hash,
                page_num,
                page_size,
                request_params_json,
                response_json,
                poi_count,
                expires_at
            )
            VALUES (
                #{searchType},
                #{areaHash},
                #{typesHash},
                #{keywordsHash},
                #{pageNum},
                #{pageSize},
                CAST(#{requestParamsJson} AS JSONB),
                CAST(#{responseJson} AS JSONB),
                #{poiCount},
                #{expiresAt}
            )
            ON CONFLICT (
                search_type,
                area_hash,
                types_hash,
                keywords_hash,
                page_num,
                page_size
            )
            DO UPDATE SET
                request_params_json = EXCLUDED.request_params_json,
                response_json = EXCLUDED.response_json,
                poi_count = EXCLUDED.poi_count,
                expires_at = EXCLUDED.expires_at,
                updated_at = now()
            """)
    int upsertResponseJson(
            @Param("searchType") String searchType,
            @Param("areaHash") String areaHash,
            @Param("typesHash") String typesHash,
            @Param("keywordsHash") String keywordsHash,
            @Param("pageNum") int pageNum,
            @Param("pageSize") int pageSize,
            @Param("requestParamsJson") String requestParamsJson,
            @Param("responseJson") String responseJson,
            @Param("poiCount") int poiCount,
            @Param("expiresAt") Instant expiresAt
    );
}
