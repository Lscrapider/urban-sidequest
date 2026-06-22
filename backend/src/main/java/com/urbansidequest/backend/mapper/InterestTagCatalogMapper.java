package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresTextArrayTypeHandler;
import com.urbansidequest.backend.domain.po.InterestTagCatalogPO;
import java.util.List;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface InterestTagCatalogMapper extends BaseMapper<InterestTagCatalogPO> {

    @Select("""
            SELECT
                id,
                tag_code,
                display_name,
                parent_tag_code,
                tag_level,
                selectable,
                max_sibling_selected,
                rollup_tag_codes,
                amap_type_codes,
                amap_keywords,
                category_group,
                sort_order,
                catalog_version,
                enabled,
                created_at,
                updated_at
            FROM interest_tag_catalog
            WHERE enabled = TRUE
            ORDER BY sort_order ASC
            """)
    @Results(id = "InterestTagCatalogResultMap", value = {
            @Result(column = "tag_code", property = "tagCode"),
            @Result(column = "display_name", property = "displayName"),
            @Result(column = "parent_tag_code", property = "parentTagCode"),
            @Result(column = "tag_level", property = "tagLevel"),
            @Result(column = "selectable", property = "selectable"),
            @Result(column = "max_sibling_selected", property = "maxSiblingSelected"),
            @Result(column = "rollup_tag_codes", property = "rollupTagCodes", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "amap_type_codes", property = "amapTypeCodes", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "amap_keywords", property = "amapKeywords", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "category_group", property = "categoryGroup"),
            @Result(column = "sort_order", property = "sortOrder"),
            @Result(column = "catalog_version", property = "catalogVersion"),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<InterestTagCatalogPO> findEnabled();

    @Select("""
            <script>
            SELECT
                id,
                tag_code,
                display_name,
                parent_tag_code,
                tag_level,
                selectable,
                max_sibling_selected,
                rollup_tag_codes,
                amap_type_codes,
                amap_keywords,
                category_group,
                sort_order,
                catalog_version,
                enabled,
                created_at,
                updated_at
            FROM interest_tag_catalog
            WHERE enabled = TRUE
              AND selectable = TRUE
              AND tag_code IN
              <foreach collection="tagCodes" item="tagCode" open="(" separator="," close=")">
                #{tagCode}
              </foreach>
            ORDER BY sort_order ASC
            </script>
            """)
    @ResultMap("InterestTagCatalogResultMap")
    List<InterestTagCatalogPO> findEnabledByTagCodes(@Param("tagCodes") List<String> tagCodes);
}
