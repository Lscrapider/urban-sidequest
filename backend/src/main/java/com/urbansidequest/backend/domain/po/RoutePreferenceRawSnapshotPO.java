package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.dto.CandidateRouteDTO;
import com.urbansidequest.backend.domain.dto.PoiCandidateDTO;
import com.urbansidequest.backend.domain.dto.PoiLinearTraceDTO;
import com.urbansidequest.backend.domain.dto.RouteAreaDTO;
import com.urbansidequest.backend.domain.dto.RouteWeatherDTO;
import com.urbansidequest.backend.domain.dto.SegmentCostDTO;
import com.urbansidequest.backend.domain.dto.UserPreferenceProfileDTO;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceRawSnapshotPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@TableName("route_preference_raw_snapshots")
public class RoutePreferenceRawSnapshotPO {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("candidate_set_id")
    private UUID candidateSetId;

    @TableField("request_id")
    private UUID requestId;

    @TableField("user_id")
    private UUID userId;

    @TableField("raw_schema_version")
    private String rawSchemaVersion;

    @TableField("generate_param_json")
    private String generateParamJson;

    @TableField("area_json")
    private String areaJson;

    @TableField("weather_json")
    private String weatherJson;

    @TableField("user_preference_profile_json")
    private String userPreferenceProfileJson;

    @TableField("interest_tag_catalog_json")
    private String interestTagCatalogJson;

    @TableField("interest_tags_json")
    private String interestTagsJson;

    @TableField("poi_semantic_mappings_json")
    private String poiSemanticMappingsJson;

    @TableField("poi_candidates_json")
    private String poiCandidatesJson;

    @TableField("poi_linear_traces_json")
    private String poiLinearTracesJson;

    @TableField("selected_routes_json")
    private String selectedRoutesJson;

    @TableField("segment_costs_json")
    private String segmentCostsJson;

    @TableField("warnings_json")
    private String warningsJson;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public static RoutePreferenceRawSnapshotPO fromPayload(
            RoutePreferenceRawSnapshotPayload payload,
            ObjectMapper objectMapper
    ) {
        RoutePreferenceRawSnapshotPO po = new RoutePreferenceRawSnapshotPO();
        po.setCandidateSetId(payload.candidateSetId());
        po.setRequestId(payload.requestId());
        po.setUserId(payload.userId());
        po.setRawSchemaVersion(payload.rawSchemaVersion());
        po.setGenerateParamJson(writeJson(objectMapper, payload.generateParam()));
        po.setAreaJson(payload.area() == null ? null : writeJson(objectMapper, payload.area()));
        po.setWeatherJson(writeJson(objectMapper, payload.weather()));
        po.setUserPreferenceProfileJson(writeJson(objectMapper, payload.userPreferenceProfile()));
        po.setInterestTagCatalogJson(writeJson(objectMapper, payload.interestTagCatalog()));
        po.setInterestTagsJson(writeJson(objectMapper, payload.interestTags()));
        po.setPoiSemanticMappingsJson(writeJson(objectMapper, payload.poiSemanticMappings()));
        po.setPoiCandidatesJson(writeJson(objectMapper, payload.poiCandidates()));
        po.setPoiLinearTracesJson(writeJson(objectMapper, payload.poiLinearTraces()));
        po.setSelectedRoutesJson(writeJson(objectMapper, payload.selectedRoutes()));
        po.setSegmentCostsJson(writeJson(objectMapper, payload.segmentCosts()));
        po.setWarningsJson(writeJson(objectMapper, payload.warnings()));
        return po;
    }

    public RoutePreferenceRawSnapshotPayload toPayload(ObjectMapper objectMapper) {
        return new RoutePreferenceRawSnapshotPayload(
                this.candidateSetId,
                this.requestId,
                this.userId,
                this.rawSchemaVersion,
                readJson(objectMapper, this.generateParamJson, RouteGenerateParam.class),
                this.areaJson == null ? null : readJson(objectMapper, this.areaJson, RouteAreaDTO.class),
                readJson(objectMapper, this.weatherJson, RouteWeatherDTO.class),
                readJson(objectMapper, this.userPreferenceProfileJson, UserPreferenceProfileDTO.class),
                readJson(objectMapper, this.interestTagCatalogJson, new TypeReference<List<InterestTagCatalogPO>>() {
                }),
                readJson(objectMapper, this.interestTagsJson, new TypeReference<List<InterestTagCatalogPO>>() {
                }),
                readJson(objectMapper, this.poiSemanticMappingsJson, new TypeReference<List<PoiSemanticMappingPO>>() {
                }),
                readJson(objectMapper, this.poiCandidatesJson, new TypeReference<List<PoiCandidateDTO>>() {
                }),
                readJson(objectMapper, this.poiLinearTracesJson, new TypeReference<List<PoiLinearTraceDTO>>() {
                }),
                readJson(objectMapper, this.selectedRoutesJson, new TypeReference<List<CandidateRouteDTO>>() {
                }),
                readJson(objectMapper, this.segmentCostsJson, new TypeReference<List<SegmentCostDTO>>() {
                }),
                readJson(objectMapper, this.warningsJson, new TypeReference<List<String>>() {
                })
        );
    }

    private static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线偏好冻结快照序列化失败", exception);
        }
    }

    private static <T> T readJson(ObjectMapper objectMapper, String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线偏好冻结快照反序列化失败", exception);
        }
    }

    private static <T> T readJson(ObjectMapper objectMapper, String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线偏好冻结快照反序列化失败", exception);
        }
    }

    public UUID getId() {
        return this.id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCandidateSetId() {
        return this.candidateSetId;
    }

    public void setCandidateSetId(UUID candidateSetId) {
        this.candidateSetId = candidateSetId;
    }

    public UUID getRequestId() {
        return this.requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getUserId() {
        return this.userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRawSchemaVersion() {
        return this.rawSchemaVersion;
    }

    public void setRawSchemaVersion(String rawSchemaVersion) {
        this.rawSchemaVersion = rawSchemaVersion;
    }

    public String getGenerateParamJson() {
        return this.generateParamJson;
    }

    public void setGenerateParamJson(String generateParamJson) {
        this.generateParamJson = generateParamJson;
    }

    public String getAreaJson() {
        return this.areaJson;
    }

    public void setAreaJson(String areaJson) {
        this.areaJson = areaJson;
    }

    public String getWeatherJson() {
        return this.weatherJson;
    }

    public void setWeatherJson(String weatherJson) {
        this.weatherJson = weatherJson;
    }

    public String getUserPreferenceProfileJson() {
        return this.userPreferenceProfileJson;
    }

    public void setUserPreferenceProfileJson(String userPreferenceProfileJson) {
        this.userPreferenceProfileJson = userPreferenceProfileJson;
    }

    public String getInterestTagCatalogJson() {
        return this.interestTagCatalogJson;
    }

    public void setInterestTagCatalogJson(String interestTagCatalogJson) {
        this.interestTagCatalogJson = interestTagCatalogJson;
    }

    public String getInterestTagsJson() {
        return this.interestTagsJson;
    }

    public void setInterestTagsJson(String interestTagsJson) {
        this.interestTagsJson = interestTagsJson;
    }

    public String getPoiSemanticMappingsJson() {
        return this.poiSemanticMappingsJson;
    }

    public void setPoiSemanticMappingsJson(String poiSemanticMappingsJson) {
        this.poiSemanticMappingsJson = poiSemanticMappingsJson;
    }

    public String getPoiCandidatesJson() {
        return this.poiCandidatesJson;
    }

    public void setPoiCandidatesJson(String poiCandidatesJson) {
        this.poiCandidatesJson = poiCandidatesJson;
    }

    public String getPoiLinearTracesJson() {
        return this.poiLinearTracesJson;
    }

    public void setPoiLinearTracesJson(String poiLinearTracesJson) {
        this.poiLinearTracesJson = poiLinearTracesJson;
    }

    public String getSelectedRoutesJson() {
        return this.selectedRoutesJson;
    }

    public void setSelectedRoutesJson(String selectedRoutesJson) {
        this.selectedRoutesJson = selectedRoutesJson;
    }

    public String getSegmentCostsJson() {
        return this.segmentCostsJson;
    }

    public void setSegmentCostsJson(String segmentCostsJson) {
        this.segmentCostsJson = segmentCostsJson;
    }

    public String getWarningsJson() {
        return this.warningsJson;
    }

    public void setWarningsJson(String warningsJson) {
        this.warningsJson = warningsJson;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
