package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.domain.enums.RoutePreferenceFeedbackLabel;
import com.urbansidequest.backend.domain.po.RoutePreferenceFeedbackPO;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface RoutePreferenceFeedbackMapper extends BaseMapper<RoutePreferenceFeedbackPO> {

    @Insert("""
            INSERT INTO route_preference_feedbacks (
                user_id,
                candidate_set_id,
                route_code,
                feedback_label
            )
            VALUES (
                #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{routeCode},
                #{feedbackLabel}
            )
            ON CONFLICT (user_id, candidate_set_id, route_code) DO UPDATE SET
                feedback_label = EXCLUDED.feedback_label,
                updated_at = now()
            """)
    int upsertFeedback(
            @Param("userId") UUID userId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("routeCode") String routeCode,
            @Param("feedbackLabel") RoutePreferenceFeedbackLabel feedbackLabel
    );
}
