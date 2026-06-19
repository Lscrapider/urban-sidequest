package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.po.UserPreferenceProfilePO;
import com.urbansidequest.backend.domain.po.UserPreferenceTagAffinityPO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

public interface UserPreferenceProfileMapper extends BaseMapper<UserPreferenceProfilePO> {

    @Select("""
            SELECT
                id,
                user_id,
                distance_sensitivity,
                budget_sensitivity,
                transfer_sensitivity,
                hidden_gem_affinity,
                profile_confidence,
                questionnaire_version,
                completed_at,
                created_at,
                updated_at
            FROM user_preference_profiles
            WHERE user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            LIMIT 1
            """)
    @Results({
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "distance_sensitivity", property = "distanceSensitivity"),
            @Result(column = "budget_sensitivity", property = "budgetSensitivity"),
            @Result(column = "transfer_sensitivity", property = "transferSensitivity"),
            @Result(column = "hidden_gem_affinity", property = "hiddenGemAffinity"),
            @Result(column = "profile_confidence", property = "profileConfidence"),
            @Result(column = "questionnaire_version", property = "questionnaireVersion"),
            @Result(column = "completed_at", property = "completedAt"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    UserPreferenceProfilePO findByUserId(@Param("userId") UUID userId);

    @Select("""
            SELECT
                affinity.id,
                affinity.user_id,
                affinity.tag_code,
                affinity.affinity,
                affinity.created_at,
                affinity.updated_at
            FROM user_preference_tag_affinities affinity
            INNER JOIN interest_tag_catalog catalog
                ON catalog.tag_code = affinity.tag_code
               AND catalog.enabled = TRUE
            WHERE affinity.user_id = #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            ORDER BY catalog.sort_order ASC, affinity.tag_code ASC
            """)
    @Results({
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "tag_code", property = "tagCode"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<UserPreferenceTagAffinityPO> findEnabledTagAffinitiesByUserId(@Param("userId") UUID userId);
}
