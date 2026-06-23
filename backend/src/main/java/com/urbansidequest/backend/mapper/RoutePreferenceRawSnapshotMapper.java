package com.urbansidequest.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.urbansidequest.backend.config.PostgresUuidTypeHandler;
import com.urbansidequest.backend.domain.po.RoutePreferenceRawSnapshotPO;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

public interface RoutePreferenceRawSnapshotMapper extends BaseMapper<RoutePreferenceRawSnapshotPO> {

    @Insert("""
            INSERT INTO route_preference_raw_snapshots (
                candidate_set_id,
                request_id,
                user_id,
                raw_schema_version,
                generate_param_json,
                area_json,
                weather_json,
                user_preference_profile_json,
                interest_tag_catalog_json,
                interest_tags_json,
                poi_semantic_mappings_json,
                poi_candidates_json,
                poi_linear_traces_json,
                selected_routes_json,
                segment_costs_json,
                warnings_json
            )
            VALUES (
                #{snapshot.candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{snapshot.requestId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{snapshot.userId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler},
                #{snapshot.rawSchemaVersion},
                CAST(#{snapshot.generateParamJson} AS JSONB),
                CAST(#{snapshot.areaJson} AS JSONB),
                CAST(#{snapshot.weatherJson} AS JSONB),
                CAST(#{snapshot.userPreferenceProfileJson} AS JSONB),
                CAST(#{snapshot.interestTagCatalogJson} AS JSONB),
                CAST(#{snapshot.interestTagsJson} AS JSONB),
                CAST(#{snapshot.poiSemanticMappingsJson} AS JSONB),
                CAST(#{snapshot.poiCandidatesJson} AS JSONB),
                CAST(#{snapshot.poiLinearTracesJson} AS JSONB),
                CAST(#{snapshot.selectedRoutesJson} AS JSONB),
                CAST(#{snapshot.segmentCostsJson} AS JSONB),
                CAST(#{snapshot.warningsJson} AS JSONB)
            )
            ON CONFLICT (candidate_set_id) DO UPDATE SET
                request_id = EXCLUDED.request_id,
                user_id = EXCLUDED.user_id,
                raw_schema_version = EXCLUDED.raw_schema_version,
                generate_param_json = EXCLUDED.generate_param_json,
                area_json = EXCLUDED.area_json,
                weather_json = EXCLUDED.weather_json,
                user_preference_profile_json = EXCLUDED.user_preference_profile_json,
                interest_tag_catalog_json = EXCLUDED.interest_tag_catalog_json,
                interest_tags_json = EXCLUDED.interest_tags_json,
                poi_semantic_mappings_json = EXCLUDED.poi_semantic_mappings_json,
                poi_candidates_json = EXCLUDED.poi_candidates_json,
                poi_linear_traces_json = EXCLUDED.poi_linear_traces_json,
                selected_routes_json = EXCLUDED.selected_routes_json,
                segment_costs_json = EXCLUDED.segment_costs_json,
                warnings_json = EXCLUDED.warnings_json,
                updated_at = now()
            """)
    int upsertSnapshot(@Param("snapshot") RoutePreferenceRawSnapshotPO snapshot);

    @Select("""
            SELECT
                id,
                candidate_set_id,
                request_id,
                user_id,
                raw_schema_version,
                generate_param_json::text AS generate_param_json,
                area_json::text AS area_json,
                weather_json::text AS weather_json,
                user_preference_profile_json::text AS user_preference_profile_json,
                interest_tag_catalog_json::text AS interest_tag_catalog_json,
                interest_tags_json::text AS interest_tags_json,
                poi_semantic_mappings_json::text AS poi_semantic_mappings_json,
                poi_candidates_json::text AS poi_candidates_json,
                poi_linear_traces_json::text AS poi_linear_traces_json,
                selected_routes_json::text AS selected_routes_json,
                segment_costs_json::text AS segment_costs_json,
                warnings_json::text AS warnings_json,
                created_at,
                updated_at
            FROM route_preference_raw_snapshots
            WHERE candidate_set_id = #{candidateSetId,typeHandler=com.urbansidequest.backend.config.PostgresUuidTypeHandler}
            """)
    @Results(id = "RoutePreferenceRawSnapshotResult", value = {
            @Result(column = "id", property = "id", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "candidate_set_id", property = "candidateSetId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "request_id", property = "requestId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "user_id", property = "userId", typeHandler = PostgresUuidTypeHandler.class),
            @Result(column = "raw_schema_version", property = "rawSchemaVersion"),
            @Result(column = "generate_param_json", property = "generateParamJson"),
            @Result(column = "area_json", property = "areaJson"),
            @Result(column = "weather_json", property = "weatherJson"),
            @Result(column = "user_preference_profile_json", property = "userPreferenceProfileJson"),
            @Result(column = "interest_tag_catalog_json", property = "interestTagCatalogJson"),
            @Result(column = "interest_tags_json", property = "interestTagsJson"),
            @Result(column = "poi_semantic_mappings_json", property = "poiSemanticMappingsJson"),
            @Result(column = "poi_candidates_json", property = "poiCandidatesJson"),
            @Result(column = "poi_linear_traces_json", property = "poiLinearTracesJson"),
            @Result(column = "selected_routes_json", property = "selectedRoutesJson"),
            @Result(column = "segment_costs_json", property = "segmentCostsJson"),
            @Result(column = "warnings_json", property = "warningsJson"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    RoutePreferenceRawSnapshotPO selectByCandidateSetId(@Param("candidateSetId") UUID candidateSetId);
}
