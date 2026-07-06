package com.urbansidequest.backend.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.enums.RiskLevel;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryRouteSummaryVO;
import com.urbansidequest.backend.domain.vo.RouteStopVO;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@TableName("route_generation_history")
public class RouteGenerationHistoryPO {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    @TableField("candidate_set_id")
    private UUID candidateSetId;

    @TableField("user_id")
    private UUID userId;

    @TableField("area_label")
    private String areaLabel;

    @TableField("route_count")
    private Integer routeCount;

    @TableField("generation_status")
    private RouteRequestStatus generationStatus;

    @TableField("generation_stage")
    private String generationStage;

    @TableField("route_code")
    private String routeCode;

    @TableField("route_index")
    private Integer routeIndex;

    @TableField("route_title")
    private String routeTitle;

    @TableField("city_name")
    private String cityName;

    @TableField("total_duration_minutes")
    private Integer totalDurationMinutes;

    @TableField("total_distance_meters")
    private Integer totalDistanceMeters;

    @TableField("risk_level")
    private RiskLevel riskLevel;

    @TableField("stop_count")
    private Integer stopCount;

    @TableField("generation_json")
    private String generationJson;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    public static List<RouteGenerationHistoryPO> fromRouteGeneration(RouteGenerationVO routeGeneration, ObjectMapper objectMapper) {
        List<GeneratedRouteVO> routes = routeGeneration.routes() == null ? List.of() : routeGeneration.routes();
        return IntStream.range(0, routes.size())
                .mapToObj(index -> fromRoute(routeGeneration, routes.get(index), index, routes.size(), objectMapper))
                .toList();
    }

    public RouteGenerationVO toRouteGenerationVO(ObjectMapper objectMapper) {
        return toRouteGenerationVO(List.of(this), objectMapper);
    }

    public static RouteGenerationVO toRouteGenerationVO(List<RouteGenerationHistoryPO> histories, ObjectMapper objectMapper) {
        if (histories == null || histories.isEmpty()) {
            throw new IllegalArgumentException("路线历史不存在");
        }
        RouteGenerationHistoryPO first = histories.get(0);
        RouteGenerationVO routeGeneration = readJson(objectMapper, first.generationJson, RouteGenerationVO.class);
        List<GeneratedRouteVO> routes = histories.stream()
                .sorted(Comparator.comparing(
                        history -> history.getRouteIndex() == null ? Integer.MAX_VALUE : history.getRouteIndex()
                ))
                .map(history -> history.toSingleRouteGenerationVO(objectMapper))
                .flatMap(generation -> generation.routes() == null ? List.<GeneratedRouteVO>of().stream() : generation.routes().stream())
                .toList();
        return new RouteGenerationVO(
                first.getCandidateSetId(),
                first.getCandidateSetId(),
                routeGeneration.userId(),
                first.getGenerationStatus() == null ? routeGeneration.status() : first.getGenerationStatus(),
                routeGeneration.area(),
                routes,
                routeGeneration.warnings(),
                first.getGenerationStage() == null ? routeGeneration.generationStage() : first.getGenerationStage(),
                null,
                RouteExecutionStatus.GENERATED
        );
    }

    public List<RouteHistoryRouteSummaryVO> toRouteSummaries(ObjectMapper objectMapper) {
        return toRouteSummaries(List.of(this));
    }

    public static List<RouteHistoryRouteSummaryVO> toRouteSummaries(List<RouteGenerationHistoryPO> histories) {
        if (histories == null) {
            return List.of();
        }
        return histories.stream()
                .sorted(Comparator.comparing(
                        history -> history.getRouteIndex() == null ? Integer.MAX_VALUE : history.getRouteIndex()
                ))
                .filter(history -> history.getRouteCode() != null)
                .map(history -> new RouteHistoryRouteSummaryVO(
                        history.getRouteCode(),
                        history.getRouteTitle(),
                        history.getCityName(),
                        history.getTotalDurationMinutes() == null ? 0 : history.getTotalDurationMinutes(),
                        history.getTotalDistanceMeters() == null ? 0 : history.getTotalDistanceMeters(),
                        history.getRiskLevel(),
                        history.getStopCount() == null ? 0 : history.getStopCount(),
                        null
                ))
                .toList();
    }

    private static RouteGenerationHistoryPO fromRoute(
            RouteGenerationVO routeGeneration,
            GeneratedRouteVO route,
            int routeIndex,
            int routeCount,
            ObjectMapper objectMapper
    ) {
        RouteGenerationHistoryPO po = new RouteGenerationHistoryPO();
        po.setCandidateSetId(routeGeneration.candidateSetId());
        po.setUserId(routeGeneration.userId());
        po.setAreaLabel(routeGeneration.area().areaLabel());
        po.setRouteCount(routeCount);
        po.setGenerationStatus(routeGeneration.status());
        po.setGenerationStage(routeGeneration.generationStage());
        po.setRouteCode(route.routeCode());
        po.setRouteIndex(routeIndex);
        po.setRouteTitle(route.title());
        po.setCityName(routeGeneration.area() == null ? null : routeGeneration.area().cityName());
        po.setTotalDurationMinutes(route.totalDurationMinutes());
        po.setTotalDistanceMeters(route.totalDistanceMeters());
        po.setRiskLevel(route.riskLevel());
        po.setStopCount(stopCount(route));
        po.setGenerationJson(writeJson(objectMapper, singleRouteGeneration(routeGeneration, route)));
        return po;
    }

    private static RouteGenerationVO singleRouteGeneration(RouteGenerationVO routeGeneration, GeneratedRouteVO route) {
        return new RouteGenerationVO(
                routeGeneration.candidateSetId(),
                routeGeneration.candidateSetId(),
                routeGeneration.userId(),
                routeGeneration.status(),
                routeGeneration.area(),
                List.of(route),
                routeGeneration.warnings(),
                routeGeneration.generationStage(),
                routeGeneration.activeRouteCode(),
                routeGeneration.executionStatus()
        );
    }

    private RouteGenerationVO toSingleRouteGenerationVO(ObjectMapper objectMapper) {
        return readJson(objectMapper, this.generationJson, RouteGenerationVO.class);
    }

    private static int stopCount(GeneratedRouteVO route) {
        List<RouteStopVO> stops = route.stops();
        return stops == null ? 0 : stops.size();
    }

    private static String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线历史快照序列化失败", exception);
        }
    }

    private static <T> T readJson(ObjectMapper objectMapper, String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线历史快照反序列化失败", exception);
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

    public UUID getUserId() {
        return this.userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAreaLabel() {
        return this.areaLabel;
    }

    public void setAreaLabel(String areaLabel) {
        this.areaLabel = areaLabel;
    }

    public Integer getRouteCount() {
        return this.routeCount;
    }

    public void setRouteCount(Integer routeCount) {
        this.routeCount = routeCount;
    }

    public RouteRequestStatus getGenerationStatus() {
        return this.generationStatus;
    }

    public void setGenerationStatus(RouteRequestStatus generationStatus) {
        this.generationStatus = generationStatus == null ? RouteRequestStatus.SUCCESS : generationStatus;
    }

    public String getGenerationStage() {
        return this.generationStage;
    }

    public void setGenerationStage(String generationStage) {
        this.generationStage = generationStage;
    }

    public String getRouteCode() {
        return this.routeCode;
    }

    public void setRouteCode(String routeCode) {
        this.routeCode = routeCode;
    }

    public Integer getRouteIndex() {
        return this.routeIndex;
    }

    public void setRouteIndex(Integer routeIndex) {
        this.routeIndex = routeIndex;
    }

    public String getRouteTitle() {
        return this.routeTitle;
    }

    public void setRouteTitle(String routeTitle) {
        this.routeTitle = routeTitle;
    }

    public String getCityName() {
        return this.cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public Integer getTotalDurationMinutes() {
        return this.totalDurationMinutes;
    }

    public void setTotalDurationMinutes(Integer totalDurationMinutes) {
        this.totalDurationMinutes = totalDurationMinutes;
    }

    public Integer getTotalDistanceMeters() {
        return this.totalDistanceMeters;
    }

    public void setTotalDistanceMeters(Integer totalDistanceMeters) {
        this.totalDistanceMeters = totalDistanceMeters;
    }

    public RiskLevel getRiskLevel() {
        return this.riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getStopCount() {
        return this.stopCount;
    }

    public void setStopCount(Integer stopCount) {
        this.stopCount = stopCount;
    }

    public String getGenerationJson() {
        return this.generationJson;
    }

    public void setGenerationJson(String generationJson) {
        this.generationJson = generationJson;
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
