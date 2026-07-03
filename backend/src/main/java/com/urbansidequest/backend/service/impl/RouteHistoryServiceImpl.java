package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.RouteActiveParam;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryGroupVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryRouteSummaryVO;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.service.RouteHistoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RouteHistoryServiceImpl implements RouteHistoryService {

    private static final int FIRST_PAGE_NUM = 1;

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 50;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    public RouteHistoryServiceImpl(RouteGenerationHistoryManage routeGenerationHistoryManage) {
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
    }

    @Override
    public List<RouteHistoryGroupVO> listHistory(AuthenticatedUser authenticatedUser, int pageNum, int pageSize) {
        int normalizedPageNum = Math.max(pageNum, FIRST_PAGE_NUM);
        int normalizedPageSize = Math.min(Math.max(pageSize, FIRST_PAGE_NUM), MAX_PAGE_SIZE);
        int offset = (normalizedPageNum - FIRST_PAGE_NUM) * normalizedPageSize;
        return this.routeGenerationHistoryManage.findByUserId(authenticatedUser.id(), normalizedPageSize, offset).stream()
                .map(this::toRouteHistoryGroupVO)
                .toList();
    }

    @Override
    public RouteGenerationVO getHistoryDetail(AuthenticatedUser authenticatedUser, UUID requestId) {
        RouteGenerationHistoryPO history = this.routeGenerationHistoryManage
                .findByUserAndRequestId(authenticatedUser.id(), requestId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        return this.routeGenerationHistoryManage.toRouteGenerationVO(history);
    }

    @Override
    public RouteGenerationVO getActiveRoute(AuthenticatedUser authenticatedUser) {
        return this.routeGenerationHistoryManage.findActiveByUserId(authenticatedUser.id())
                .map(this.routeGenerationHistoryManage::toRouteGenerationVO)
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
        this.routeGenerationHistoryManage.activateRoute(authenticatedUser.id(), requestId, param.getRouteCode());
        return this.getHistoryDetail(authenticatedUser, requestId);
    }

    private RouteHistoryGroupVO toRouteHistoryGroupVO(RouteGenerationHistoryPO history) {
        RouteGenerationVO routeGeneration = this.routeGenerationHistoryManage.toRouteGenerationVO(history);
        return new RouteHistoryGroupVO(
                history.getRequestId(),
                history.getCandidateSetId(),
                history.getAreaLabel(),
                history.getCreatedAt(),
                history.getActiveRouteCode(),
                history.getExecutionStatus(),
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
}
