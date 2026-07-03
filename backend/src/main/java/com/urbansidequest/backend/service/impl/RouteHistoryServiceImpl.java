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
import org.springframework.stereotype.Service;

@Service
public class RouteHistoryServiceImpl implements RouteHistoryService {

    private static final int FIRST_PAGE_NUM = 1;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final RouteExecutionManage routeExecutionManage;
    private final UserManage userManage;

    public RouteHistoryServiceImpl(
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            RouteExecutionManage routeExecutionManage,
            UserManage userManage
    ) {
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routeExecutionManage = routeExecutionManage;
        this.userManage = userManage;
    }

    @Override
    public List<RouteHistoryGroupVO> listHistory(AuthenticatedUser authenticatedUser, int pageNum, int pageSize) {
        int normalizedPageNum = Math.max(pageNum, FIRST_PAGE_NUM);
        int normalizedPageSize = Math.min(Math.max(pageSize, FIRST_PAGE_NUM), MAX_PAGE_SIZE);
        int offset = (normalizedPageNum - FIRST_PAGE_NUM) * normalizedPageSize;
        List<RouteGenerationHistoryPO> histories = this.routeGenerationHistoryManage
                .findByUserId(authenticatedUser.id(), normalizedPageSize, offset);
        Map<UUID, RouteExecutionPO> executionsByRequestId = this.routeExecutionManage.findLatestByRequestIds(
                authenticatedUser.id(),
                histories.stream().map(RouteGenerationHistoryPO::getRequestId).toList()
        );
        return histories.stream()
                .map(history -> this.toRouteHistoryGroupVO(history, executionsByRequestId.get(history.getRequestId())))
                .toList();
    }

    @Override
    public RouteGenerationVO getHistoryDetail(AuthenticatedUser authenticatedUser, UUID requestId) {
        RouteGenerationHistoryPO history = this.routeGenerationHistoryManage
                .findByUserAndRequestId(authenticatedUser.id(), requestId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        RouteExecutionPO execution = this.routeExecutionManage
                .findLatestByRequestId(authenticatedUser.id(), requestId)
                .orElse(null);
        return this.withExecution(this.routeGenerationHistoryManage.toRouteGenerationVO(history), execution);
    }

    @Override
    public RouteGenerationVO getActiveRoute(AuthenticatedUser authenticatedUser) {
        return this.routeExecutionManage.findInProgressByUserId(authenticatedUser.id())
                .flatMap(execution -> this.routeGenerationHistoryManage
                        .findByUserAndRequestId(authenticatedUser.id(), execution.getRequestId())
                        .map(history -> this.withExecution(this.routeGenerationHistoryManage.toRouteGenerationVO(history), execution)))
                .orElse(null);
    }

    @Override
    public RouteGenerationVO activateRoute(AuthenticatedUser authenticatedUser, UUID requestId, RouteActiveParam param) {
        RouteGenerationVO routeGeneration = this.getHistoryDetail(authenticatedUser, requestId);
        boolean routeExists = routeGeneration.routes().stream()
                .map(GeneratedRouteVO::routeCode)
                .anyMatch(routeCode -> routeCode.equals(param.getRouteCode()));
        if (!routeExists) {
            throw new IllegalArgumentException("路线不属于当前历史记录");
        }
        this.routeExecutionManage.activateRoute(authenticatedUser.id(), requestId, param.getRouteCode());
        RouteExecutionPO execution = this.routeExecutionManage
                .findInProgressByUserId(authenticatedUser.id())
                .orElseThrow(() -> new IllegalStateException("路线执行记录创建失败"));
        return this.withExecution(routeGeneration, execution);
    }

    @Override
    public RouteGenerationVO completeActiveRoute(AuthenticatedUser authenticatedUser, UUID requestId, RouteActiveParam param) {
        RouteExecutionPO activeExecution = this.routeExecutionManage.findInProgressByUserId(authenticatedUser.id())
                .orElseThrow(() -> new IllegalArgumentException("当前没有进行中的路线"));
        if (!requestId.equals(activeExecution.getRequestId()) || !param.getRouteCode().equals(activeExecution.getRouteCode())) {
            throw new IllegalArgumentException("路线不是当前进行中的路线");
        }
        RouteGenerationVO routeGeneration = this.getHistoryDetail(authenticatedUser, requestId);
        GeneratedRouteVO completedRoute = routeGeneration.routes().stream()
                .filter(route -> param.getRouteCode().equals(route.routeCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("路线不属于当前历史记录"));
        this.routeExecutionManage.completeActiveRoute(authenticatedUser.id(), requestId, param.getRouteCode());
        this.userManage.incrementRouteAssetStats(authenticatedUser.id(), completedRoute.totalDistanceMeters());
        return this.getHistoryDetail(authenticatedUser, requestId);
    }

    private RouteHistoryGroupVO toRouteHistoryGroupVO(RouteGenerationHistoryPO history, RouteExecutionPO execution) {
        RouteGenerationVO routeGeneration = this.routeGenerationHistoryManage.toRouteGenerationVO(history);
        return new RouteHistoryGroupVO(
                history.getRequestId(),
                history.getCandidateSetId(),
                history.getAreaLabel(),
                history.getCreatedAt(),
                history.getGenerationStatus(),
                history.getGenerationStage(),
                execution == null ? null : execution.getRouteCode(),
                execution == null ? RouteExecutionStatus.GENERATED : execution.getExecutionStatus(),
                routeGeneration.routes().stream()
                        .map(this::toRouteHistoryRouteSummaryVO)
                        .toList()
        );
    }

    private RouteHistoryRouteSummaryVO toRouteHistoryRouteSummaryVO(GeneratedRouteVO route) {
        return new RouteHistoryRouteSummaryVO(
                route.routeCode(),
                route.title(),
                route.totalDurationMinutes(),
                route.totalDistanceMeters(),
                route.riskLevel()
        );
    }

    private RouteGenerationVO withExecution(RouteGenerationVO routeGeneration, RouteExecutionPO execution) {
        return new RouteGenerationVO(
                routeGeneration.requestId(),
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
}
