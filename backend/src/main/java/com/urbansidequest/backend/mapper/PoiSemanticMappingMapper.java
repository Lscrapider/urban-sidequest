package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresTextArrayTypeHandler;
import com.urbansidequest.backend.domain.po.PoiSemanticMappingPO;
import java.util.List;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

public interface PoiSemanticMappingMapper extends BaseMapper<PoiSemanticMappingPO> {

    @Select("""
            SELECT
                id,
                mapping_code,
                display_name,
                amap_type_prefixes,
                keyword_patterns,
                category_group,
                interest_tag_codes,
                is_classic,
                is_local,
                is_photo_friendly,
                is_night_friendly,
                is_quiet,
                is_hidden_gem,
                weather_sensitivity,
                priority,
                enabled,
                created_at,
                updated_at
            FROM poi_semantic_mapping
            WHERE enabled = TRUE
            ORDER BY priority ASC, mapping_code ASC
            """)
    @Results({
            @Result(column = "mapping_code", property = "mappingCode"),
            @Result(column = "display_name", property = "displayName"),
            @Result(column = "amap_type_prefixes", property = "amapTypePrefixes", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "keyword_patterns", property = "keywordPatterns", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "category_group", property = "categoryGroup"),
            @Result(column = "interest_tag_codes", property = "interestTagCodes", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "is_classic", property = "classic"),
            @Result(column = "is_local", property = "local"),
            @Result(column = "is_photo_friendly", property = "photoFriendly"),
            @Result(column = "is_night_friendly", property = "nightFriendly"),
            @Result(column = "is_quiet", property = "quiet"),
            @Result(column = "is_hidden_gem", property = "hiddenGem"),
            @Result(column = "weather_sensitivity", property = "weatherSensitivity"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<PoiSemanticMappingPO> findEnabledMappings();
}
