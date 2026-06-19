package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("route_preference_judgments")
public class RoutePreferenceJudgmentPO {

    @TableId("id")
    private UUID id;

    @TableField("candidate_set_id")
    private UUID candidateSetId;

    @TableField("judge_type")
    private String judgeType;

    @TableField("judge_model")
    private String judgeModel;

    @TableField("judge_prompt_version")
    private String judgePromptVersion;

    @TableField("ranking_json")
    private String rankingJson;

    @TableField("accepted_route_codes_json")
    private String acceptedRouteCodesJson;

    @TableField("rejected_route_codes_json")
    private String rejectedRouteCodesJson;

    @TableField("reason_codes_json")
    private String reasonCodesJson;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("status")
    private String status;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("completed_at")
    private Instant completedAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
