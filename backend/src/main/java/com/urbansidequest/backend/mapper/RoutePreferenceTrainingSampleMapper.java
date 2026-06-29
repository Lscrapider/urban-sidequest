package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.domain.po.RoutePreferenceTrainingSamplePO;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RoutePreferenceTrainingSampleMapper extends BaseMapper<RoutePreferenceTrainingSamplePO> {

    @Insert("""
            INSERT INTO route_preference_training_samples (
                candidate_set_id,
                request_id,
                user_id,
                route_code,
                feature_schema_version,
                stop_matrix_json,
                segment_matrix_json,
                route_derived_vector_json,
                context_cross_vector_json,
                context_json,
                sample_status
            )
            VALUES (
                #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{requestId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{routeCode},
                #{featureSchemaVersion},
                CAST(#{stopMatrixJson} AS JSONB),
                CAST(#{segmentMatrixJson} AS JSONB),
                CAST(#{routeDerivedVectorJson} AS JSONB),
                CAST(#{contextCrossVectorJson} AS JSONB),
                CAST(#{contextJson} AS JSONB),
                #{sampleStatus}
            )
            ON CONFLICT (candidate_set_id, route_code) DO UPDATE SET
                feature_schema_version = EXCLUDED.feature_schema_version,
                stop_matrix_json = EXCLUDED.stop_matrix_json,
                segment_matrix_json = EXCLUDED.segment_matrix_json,
                route_derived_vector_json = EXCLUDED.route_derived_vector_json,
                context_cross_vector_json = EXCLUDED.context_cross_vector_json,
                context_json = EXCLUDED.context_json,
                sample_status = EXCLUDED.sample_status,
                updated_at = now()
            """)
    int upsertSample(
            @Param("candidateSetId") UUID candidateSetId,
            @Param("requestId") UUID requestId,
            @Param("userId") UUID userId,
            @Param("routeCode") String routeCode,
            @Param("featureSchemaVersion") String featureSchemaVersion,
            @Param("stopMatrixJson") String stopMatrixJson,
            @Param("segmentMatrixJson") String segmentMatrixJson,
            @Param("routeDerivedVectorJson") String routeDerivedVectorJson,
            @Param("contextCrossVectorJson") String contextCrossVectorJson,
            @Param("contextJson") String contextJson,
            @Param("sampleStatus") String sampleStatus
    );

    @Insert("""
            INSERT INTO route_preference_training_samples (
                candidate_set_id,
                request_id,
                user_id,
                route_code,
                feature_schema_version,
                stop_matrix_json,
                segment_matrix_json,
                route_derived_vector_json,
                context_cross_vector_json,
                context_json,
                sample_status
            )
            VALUES (
                #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{requestId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{routeCode},
                #{featureSchemaVersion},
                CAST(#{stopMatrixJson} AS JSONB),
                CAST(#{segmentMatrixJson} AS JSONB),
                CAST(#{routeDerivedVectorJson} AS JSONB),
                CAST(#{contextCrossVectorJson} AS JSONB),
                CAST(#{contextJson} AS JSONB),
                #{sampleStatus}
            )
            ON CONFLICT (candidate_set_id, route_code) DO UPDATE SET
                request_id = EXCLUDED.request_id,
                user_id = EXCLUDED.user_id,
                feature_schema_version = EXCLUDED.feature_schema_version,
                stop_matrix_json = EXCLUDED.stop_matrix_json,
                segment_matrix_json = EXCLUDED.segment_matrix_json,
                route_derived_vector_json = EXCLUDED.route_derived_vector_json,
                context_cross_vector_json = EXCLUDED.context_cross_vector_json,
                context_json = EXCLUDED.context_json,
                updated_at = now()
            """)
    int upsertFeatureSnapshot(
            @Param("candidateSetId") UUID candidateSetId,
            @Param("requestId") UUID requestId,
            @Param("userId") UUID userId,
            @Param("routeCode") String routeCode,
            @Param("featureSchemaVersion") String featureSchemaVersion,
            @Param("stopMatrixJson") String stopMatrixJson,
            @Param("segmentMatrixJson") String segmentMatrixJson,
            @Param("routeDerivedVectorJson") String routeDerivedVectorJson,
            @Param("contextCrossVectorJson") String contextCrossVectorJson,
            @Param("contextJson") String contextJson,
            @Param("sampleStatus") String sampleStatus
    );

    @Update("""
            UPDATE route_preference_training_samples
            SET label_source = #{labelSource},
                label_json = CAST(#{labelJson} AS JSONB),
                sample_weight = #{sampleWeight},
                sample_status = #{sampleStatus},
                updated_at = now()
            WHERE candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            """)
    int markCandidateSetTrainReady(
            @Param("candidateSetId") UUID candidateSetId,
            @Param("labelSource") String labelSource,
            @Param("labelJson") String labelJson,
            @Param("sampleWeight") BigDecimal sampleWeight,
            @Param("sampleStatus") String sampleStatus
    );

    @Select("""
            SELECT DISTINCT candidate_set_id::text
            FROM route_preference_training_samples
            WHERE feature_schema_version <> #{featureSchemaVersion}
            ORDER BY candidate_set_id
            """)
    List<String> findOutdatedCandidateSetIds(@Param("featureSchemaVersion") String featureSchemaVersion);
}
