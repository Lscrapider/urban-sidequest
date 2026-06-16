package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresTextArrayTypeHandler;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import java.util.List;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InterestTagCatalogMapper extends BaseMapper<InterestTagCatalogPO> {

    @Select("""
            <script>
            SELECT
                id,
                tag_code,
                display_name,
                amap_type_codes,
                amap_keywords,
                category_group,
                sort_order,
                enabled,
                created_at,
                updated_at
            FROM interest_tag_catalog
            WHERE enabled = TRUE
              AND tag_code IN
              <foreach collection="tagCodes" item="tagCode" open="(" separator="," close=")">
                #{tagCode}
              </foreach>
            ORDER BY sort_order ASC
            </script>
            """)
    @Results({
            @Result(column = "tag_code", property = "tagCode"),
            @Result(column = "display_name", property = "displayName"),
            @Result(column = "amap_type_codes", property = "amapTypeCodes", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "amap_keywords", property = "amapKeywords", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "category_group", property = "categoryGroup"),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<InterestTagCatalogPO> findEnabledByTagCodes(@Param("tagCodes") List<String> tagCodes);
}
