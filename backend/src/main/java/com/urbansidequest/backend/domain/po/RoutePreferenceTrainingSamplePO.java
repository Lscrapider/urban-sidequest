package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@TableName("route_preference_training_samples")
public class RoutePreferenceTrainingSamplePO {

    @TableId("id")
    private UUID id;

    @TableField("candidate_set_id")
    private UUID candidateSetId;

    @TableField("request_id")
    private UUID requestId;

    @TableField("user_id")
    private UUID userId;

    @TableField("route_code")
    private String routeCode;

    @TableField("generated_route_id")
    private UUID generatedRouteId;

    @TableField("feature_schema_version")
    private String featureSchemaVersion;

    @TableField("stop_matrix_json")
    private String stopMatrixJson;

    @TableField("segment_matrix_json")
    private String segmentMatrixJson;

    @TableField("route_derived_vector_json")
    private String routeDerivedVectorJson;

    @TableField("context_cross_vector_json")
    private String contextCrossVectorJson;

    @TableField("intra_set_vector_json")
    private String intraSetVectorJson;

    @TableField("context_json")
    private String contextJson;

    @TableField("label_source")
    private String labelSource;

    @TableField("label_json")
    private String labelJson;

    @TableField("sample_weight")
    private BigDecimal sampleWeight;

    @TableField("sample_status")
    private String sampleStatus;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
