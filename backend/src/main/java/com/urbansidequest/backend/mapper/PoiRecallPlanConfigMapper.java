package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresTextArrayTypeHandler;
import com.urbansidequest.backend.domain.po.PoiRecallPlanConfigPO;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

public interface PoiRecallPlanConfigMapper extends BaseMapper<PoiRecallPlanConfigPO> {

    @Select("""
            <script>
            SELECT
                id,
                plan_code,
                plan_version,
                plan_type,
                trigger_type,
                trigger_value,
                tag_code,
                amap_type_codes,
                amap_keywords,
                role_hint,
                category_group_hint,
                intent_tags,
                priority,
                enabled,
                reason_seed,
                created_at,
                updated_at
            FROM poi_recall_plan_config
            WHERE enabled = TRUE
              AND plan_type = 'INTEREST_TAG'
              AND tag_code IN
              <foreach collection="tagCodes" item="tagCode" open="(" separator="," close=")">
                #{tagCode}
              </foreach>
            ORDER BY priority ASC, plan_code ASC
            </script>
            """)
    @Results({
            @Result(column = "plan_code", property = "planCode"),
            @Result(column = "plan_version", property = "planVersion"),
            @Result(column = "plan_type", property = "planType"),
            @Result(column = "trigger_type", property = "triggerType"),
            @Result(column = "trigger_value", property = "triggerValue"),
            @Result(column = "tag_code", property = "tagCode"),
            @Result(column = "amap_type_codes", property = "amapTypeCodes", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "amap_keywords", property = "amapKeywords", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "role_hint", property = "roleHint"),
            @Result(column = "category_group_hint", property = "categoryGroupHint"),
            @Result(column = "intent_tags", property = "intentTags", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "reason_seed", property = "reasonSeed"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<PoiRecallPlanConfigPO> findEnabledInterestPlansByTagCodes(@Param("tagCodes") List<String> tagCodes);

    @Select("""
            <script>
            SELECT
                id,
                plan_code,
                plan_version,
                plan_type,
                trigger_type,
                trigger_value,
                tag_code,
                amap_type_codes,
                amap_keywords,
                role_hint,
                category_group_hint,
                intent_tags,
                priority,
                enabled,
                reason_seed,
                created_at,
                updated_at
            FROM poi_recall_plan_config
            WHERE enabled = TRUE
              AND plan_type IN
              <foreach collection="planTypes" item="planType" open="(" separator="," close=")">
                #{planType}
              </foreach>
            ORDER BY priority ASC, plan_code ASC
            </script>
            """)
    @Results({
            @Result(column = "plan_code", property = "planCode"),
            @Result(column = "plan_version", property = "planVersion"),
            @Result(column = "plan_type", property = "planType"),
            @Result(column = "trigger_type", property = "triggerType"),
            @Result(column = "trigger_value", property = "triggerValue"),
            @Result(column = "tag_code", property = "tagCode"),
            @Result(column = "amap_type_codes", property = "amapTypeCodes", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "amap_keywords", property = "amapKeywords", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "role_hint", property = "roleHint"),
            @Result(column = "category_group_hint", property = "categoryGroupHint"),
            @Result(column = "intent_tags", property = "intentTags", typeHandler = PostgresTextArrayTypeHandler.class),
            @Result(column = "reason_seed", property = "reasonSeed"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<PoiRecallPlanConfigPO> findEnabledPlansByPlanTypes(@Param("planTypes") List<String> planTypes);
}
