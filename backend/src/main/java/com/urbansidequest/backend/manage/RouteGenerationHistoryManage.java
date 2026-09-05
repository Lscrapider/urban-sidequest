package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryRouteSummaryVO;
import com.urbansidequest.backend.mapper.RouteGenerationHistoryMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RouteGenerationHistoryManage extends ServiceImpl<RouteGenerationHistoryMapper, RouteGenerationHistoryPO> {

    private final ObjectMapper objectMapper;

    public RouteGenerationHistoryManage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void createPendingHistory(RouteGenerationVO routeGeneration) {
        this.baseMapper.insertPendingHistory(
                RouteGenerationHistoryPO.fromPendingGeneration(routeGeneration, this.objectMapper)
        );
    }

    @Transactional
    public void deletePendingHistory(UUID candidateSetId, UUID userId) {
        this.baseMapper.deletePendingHistory(candidateSetId, userId);
    }

    @Transactional
    public void upsertHistory(RouteGenerationVO routeGeneration) {
        List<RouteGenerationHistoryPO> histories = RouteGenerationHistoryPO.fromRouteGeneration(routeGeneration, this.objectMapper);
        if (histories.isEmpty()) {
            this.baseMapper.updateGenerationState(
                    routeGeneration.candidateSetId(),
                    routeGeneration.userId(),
                    routeGeneration.area() == null ? null : routeGeneration.area().areaLabel(),
                    0,
                    routeGeneration.status(),
                    routeGeneration.generationStage()
            );
            return;
        }
        List<String> routeCodes = histories.stream()
                .map(RouteGenerationHistoryPO::getRouteCode)
                .toList();
        histories.forEach(this.baseMapper::upsertRoute);
        this.baseMapper.deleteRoutesNotIn(routeGeneration.candidateSetId(), routeCodes);
    }

    public List<List<RouteGenerationHistoryPO>> findGroupsByUserId(UUID userId, int pageSize, int offset) {
        return this.groupByCandidateSetId(this.baseMapper.selectByUserId(userId, pageSize, offset));
    }

    public List<RouteGenerationHistoryPO> findByUserAndCandidateSetId(UUID userId, UUID candidateSetId) {
        return this.baseMapper.selectByUserAndCandidateSetId(userId, candidateSetId);
    }

    public Optional<RouteGenerationVO> findRouteGenerationByUserAndCandidateSetId(UUID userId, UUID candidateSetId) {
        List<RouteGenerationHistoryPO> histories = this.findByUserAndCandidateSetId(userId, candidateSetId);
        if (histories.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(this.toRouteGenerationVO(histories));
    }

    public RouteGenerationVO toRouteGenerationVO(RouteGenerationHistoryPO history) {
        return history.toRouteGenerationVO(this.objectMapper);
    }

    public RouteGenerationVO toRouteGenerationVO(List<RouteGenerationHistoryPO> histories) {
        return RouteGenerationHistoryPO.toRouteGenerationVO(histories, this.objectMapper);
    }

    public List<RouteHistoryRouteSummaryVO> toRouteSummaries(RouteGenerationHistoryPO history) {
        return history.toRouteSummaries(this.objectMapper);
    }

    public List<RouteHistoryRouteSummaryVO> toRouteSummaries(List<RouteGenerationHistoryPO> histories) {
        return RouteGenerationHistoryPO.toRouteSummaries(histories);
    }

    private List<List<RouteGenerationHistoryPO>> groupByCandidateSetId(List<RouteGenerationHistoryPO> histories) {
        Map<UUID, List<RouteGenerationHistoryPO>> grouped = new LinkedHashMap<>();
        for (RouteGenerationHistoryPO history : histories) {
            grouped.computeIfAbsent(history.getCandidateSetId(), ignored -> new ArrayList<>()).add(history);
        }
        return new ArrayList<>(grouped.values());
    }
}
