package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.param.RouteActiveParam;
import com.urbansidequest.backend.domain.po.RouteExecutionPO;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryGroupVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryRouteSummaryVO;
import com.urbansidequest.backend.manage.RouteExecutionManage;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.manage.UserManage;
import com.urbansidequest.backend.service.RouteHistoryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RouteHistoryServiceImpl implements RouteHistoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteHistoryServiceImpl.class);

    private static final int FIRST_PAGE_NUM = 1;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final RouteExecutionManage routeExecutionManage;
    private final UserManage userManage;
    private final RouteMapSnapshotSupport routeMapSnapshotSupport;

    public RouteHistoryServiceImpl(
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            RouteExecutionManage routeExecutionManage,
            UserManage userManage,
            RouteMapSnapshotSupport routeMapSnapshotSupport
    ) {
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routeExecutionManage = routeExecutionManage;
        this.userManage = userManage;
        this.routeMapSnapshotSupport = routeMapSnapshotSupport;
    }

    @Override
    public List<RouteHistoryGroupVO> listHistory(AuthenticatedUser authenticatedUser, int pageNum, int pageSize) {
        int normalizedPageNum = Math.max(pageNum, FIRST_PAGE_NUM);
        int normalizedPageSize = Math.min(Math.max(pageSize, FIRST_PAGE_NUM), MAX_PAGE_SIZE);
        int offset = (normalizedPageNum - FIRST_PAGE_NUM) * normalizedPageSize;
        List<List<RouteGenerationHistoryPO>> historyGroups = this.routeGenerationHistoryManage
                .findGroupsByUserId(authenticatedUser.id(), normalizedPageSize, offset);
        Map<UUID, RouteExecutionPO> executionsByCandidateSetId = this.routeExecutionManage.findLatestByCandidateSetIds(
                authenticatedUser.id(),
                historyGroups.stream().map(group -> group.get(0).getCandidateSetId()).toList()
        );
        return historyGroups.stream()
                .map(histories -> this.toRouteHistoryGroupVO(
                        histories,
                        executionsByCandidateSetId.get(histories.get(0).getCandidateSetId())
                ))
                .toList();
    }

    @Override
    public RouteGenerationVO getHistoryDetail(AuthenticatedUser authenticatedUser, UUID candidateSetId) {
        RouteGenerationVO routeGeneration = this.routeGenerationHistoryManage
                .findRouteGenerationByUserAndCandidateSetId(authenticatedUser.id(), candidateSetId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        RouteExecutionPO execution = this.routeExecutionManage
                .findLatestByCandidateSetId(authenticatedUser.id(), candidateSetId)
                .orElse(null);
        return this.withExecution(routeGeneration, execution);
    }

    @Override
    public RouteGenerationVO getActiveRoute(AuthenticatedUser authenticatedUser) {
        return this.routeExecutionManage.findInProgressByUserId(authenticatedUser.id())
                .flatMap(execution -> this.routeGenerationHistoryManage
                        .findRouteGenerationByUserAndCandidateSetId(authenticatedUser.id(), execution.getCandidateSetId())
                        .map(routeGeneration -> this.withExecution(routeGeneration, execution)))
                .orElse(null);
    }

    @Override
    public RouteGenerationVO activateRoute(AuthenticatedUser authenticatedUser, UUID candidateSetId, RouteActiveParam param) {
        RouteGenerationVO routeGeneration = this.getHistoryDetail(authenticatedUser, candidateSetId);
        boolean routeExists = routeGeneration.routes().stream()
                .map(GeneratedRouteVO::routeCode)
                .anyMatch(routeCode -> routeCode.equals(param.getRouteCode()));
        if (!routeExists) {
            throw new IllegalArgumentException("路线不属于当前历史记录");
        }
        this.routeExecutionManage.activateRoute(authenticatedUser.id(), candidateSetId, param.getRouteCode());
        RouteExecutionPO execution = this.routeExecutionManage
                .findInProgressByUserId(authenticatedUser.id())
                .orElseThrow(() -> new IllegalStateException("路线执行记录创建失败"));
        return this.withExecution(routeGeneration, execution);
    }

    @Override
    public RouteGenerationVO completeActiveRoute(AuthenticatedUser authenticatedUser, UUID candidateSetId, RouteActiveParam param) {
        RouteExecutionPO activeExecution = this.routeExecutionManage.findInProgressByUserId(authenticatedUser.id())
                .orElseThrow(() -> new IllegalArgumentException("当前没有进行中的路线"));
        if (!candidateSetId.equals(activeExecution.getCandidateSetId()) || !param.getRouteCode().equals(activeExecution.getRouteCode())) {
            throw new IllegalArgumentException("路线不是当前进行中的路线");
        }
        RouteGenerationVO routeGeneration = this.getHistoryDetail(authenticatedUser, candidateSetId);
        GeneratedRouteVO completedRoute = routeGeneration.routes().stream()
                .filter(route -> param.getRouteCode().equals(route.routeCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("路线不属于当前历史记录"));
        this.routeExecutionManage.completeActiveRoute(authenticatedUser.id(), candidateSetId, param.getRouteCode());
        this.userManage.incrementRouteAssetStats(authenticatedUser.id(), completedRoute.totalDistanceMeters());
        RouteExecutionPO completedExecution = this.routeExecutionManage.findLatestByCandidateSetId(authenticatedUser.id(), candidateSetId)
                .orElse(null);
        if (completedExecution != null && completedExecution.getExecutionStatus() == RouteExecutionStatus.COMPLETED) {
            this.trySaveMapSnapshot(authenticatedUser.id(), candidateSetId, completedExecution, completedRoute);
        }
        return this.getHistoryDetail(authenticatedUser, candidateSetId);
    }

    private RouteHistoryGroupVO toRouteHistoryGroupVO(List<RouteGenerationHistoryPO> histories, RouteExecutionPO execution) {
        RouteGenerationHistoryPO first = histories.get(0);
        RouteGenerationVO routeGeneration = this.routeGenerationHistoryManage.toRouteGenerationVO(histories);
        return new RouteHistoryGroupVO(
                first.getCandidateSetId(),
                first.getCandidateSetId(),
                first.getAreaLabel(),
                first.getCreatedAt(),
                first.getGenerationStatus(),
                first.getGenerationStage(),
                execution == null ? null : execution.getRouteCode(),
                execution == null ? RouteExecutionStatus.GENERATED : execution.getExecutionStatus(),
                routeGeneration.routes().stream()
                        .map(route -> this.toRouteHistoryRouteSummaryVO(
                                route,
                                routeGeneration.area() == null ? null : routeGeneration.area().cityName(),
                                execution
                        ))
                        .toList()
        );
    }

    private RouteHistoryRouteSummaryVO toRouteHistoryRouteSummaryVO(GeneratedRouteVO route, String cityName, RouteExecutionPO execution) {
        return new RouteHistoryRouteSummaryVO(
                route.routeCode(),
                route.title(),
                cityName,
                route.totalDurationMinutes(),
                route.totalDistanceMeters(),
                route.riskLevel(),
                route.stops() == null ? 0 : route.stops().size(),
                this.mapSnapshotUrlFor(route, execution)
        );
    }

    private String mapSnapshotUrlFor(GeneratedRouteVO route, RouteExecutionPO execution) {
        if (execution == null || execution.getExecutionStatus() != RouteExecutionStatus.COMPLETED) {
            return null;
        }
        if (!route.routeCode().equals(execution.getRouteCode())) {
            return null;
        }
        return execution.getMapSnapshotUrl();
    }

    private RouteGenerationVO withExecution(RouteGenerationVO routeGeneration, RouteExecutionPO execution) {
        return new RouteGenerationVO(
                routeGeneration.candidateSetId(),
                routeGeneration.candidateSetId(),
                routeGeneration.userId(),
                routeGeneration.status(),
                routeGeneration.area(),
                routeGeneration.routes(),
                routeGeneration.warnings(),
                routeGeneration.generationStage(),
                execution == null ? null : execution.getRouteCode(),
                execution == null ? RouteExecutionStatus.GENERATED : execution.getExecutionStatus()
        );
    }

    private void trySaveMapSnapshot(UUID userId, UUID candidateSetId, RouteExecutionPO execution, GeneratedRouteVO route) {
        try {
            this.routeMapSnapshotSupport.ensureSnapshot(userId, candidateSetId, execution, route);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "路线地图快照生成失败 userId={} candidateSetId={} routeCode={}",
                    userId,
                    candidateSetId,
                    route.routeCode(),
                    exception
            );
        }
    }
}
