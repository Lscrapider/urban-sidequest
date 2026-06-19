package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.domain.po.RoutePreferenceJudgmentPO;
import java.math.BigDecimal;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface RoutePreferenceJudgmentMapper extends BaseMapper<RoutePreferenceJudgmentPO> {

    @Insert("""
            INSERT INTO route_preference_judgments (
                id,
                candidate_set_id,
                judge_type,
                judge_model,
                judge_prompt_version,
                ranking_json,
                accepted_route_codes_json,
                rejected_route_codes_json,
                reason_codes_json,
                confidence,
                status,
                completed_at
            )
            VALUES (
                #{judgmentId},
                #{candidateSetId},
                #{judgeType},
                #{judgeModel},
                #{judgePromptVersion},
                CAST(#{rankingJson} AS JSONB),
                CAST(#{acceptedRouteCodesJson} AS JSONB),
                CAST(#{rejectedRouteCodesJson} AS JSONB),
                CAST(#{reasonCodesJson} AS JSONB),
                #{confidence},
                #{status},
                now()
            )
            """)
    int insertJudgment(
            @Param("judgmentId") UUID judgmentId,
            @Param("candidateSetId") UUID candidateSetId,
            @Param("judgeType") String judgeType,
            @Param("judgeModel") String judgeModel,
            @Param("judgePromptVersion") String judgePromptVersion,
            @Param("rankingJson") String rankingJson,
            @Param("acceptedRouteCodesJson") String acceptedRouteCodesJson,
            @Param("rejectedRouteCodesJson") String rejectedRouteCodesJson,
            @Param("reasonCodesJson") String reasonCodesJson,
            @Param("confidence") BigDecimal confidence,
            @Param("status") String status
    );
}
