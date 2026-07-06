package com.urbansidequest.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.po.RouteExecutionPO;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import com.urbansidequest.backend.domain.po.RouteSharePO;
import com.urbansidequest.backend.domain.vo.GeneratedRouteVO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryRouteSummaryVO;
import com.urbansidequest.backend.domain.vo.RouteShareVO;
import com.urbansidequest.backend.manage.RouteExecutionManage;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.manage.RouteShareManage;
import com.urbansidequest.backend.provider.route.share.RouteShareImageObjectStore.StoredRouteShareImage;
import com.urbansidequest.backend.provider.route.share.RouteStaticMapImageBuilder;
import com.urbansidequest.backend.service.RouteShareService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RouteShareServiceImpl implements RouteShareService {

    private static final int MAX_SHARE_PAGE_SIZE = 50;

    private final RouteShareManage routeShareManage;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final RouteExecutionManage routeExecutionManage;

    private final RouteStaticMapImageBuilder routeStaticMapImageBuilder;

    private final RouteMapSnapshotSupport routeMapSnapshotSupport;

    public RouteShareServiceImpl(
            RouteShareManage routeShareManage,
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            RouteExecutionManage routeExecutionManage,
            RouteStaticMapImageBuilder routeStaticMapImageBuilder,
            RouteMapSnapshotSupport routeMapSnapshotSupport
    ) {
        this.routeShareManage = routeShareManage;
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routeExecutionManage = routeExecutionManage;
        this.routeStaticMapImageBuilder = routeStaticMapImageBuilder;
        this.routeMapSnapshotSupport = routeMapSnapshotSupport;
    }

    @Override
    public List<RouteShareVO> listLatestShares(int pageSize) {
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_SHARE_PAGE_SIZE);
        return this.routeShareManage.findLatest(normalizedPageSize).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public RouteShareVO shareCompletedRoute(
            AuthenticatedUser authenticatedUser,
            UUID candidateSetId,
            String routeCode,
            String shareText
    ) {
        CompletedRouteContext completedRouteContext = this.validateCompletedRoute(authenticatedUser.id(), candidateSetId, routeCode);
        String normalizedShareText = StrUtil.blankToDefault(shareText, "这条路线走下来很顺，适合直接照着走。").trim();
        if (normalizedShareText.length() > 240) {
            throw new IllegalArgumentException("分享文字不能超过 240 个字");
        }
        StoredRouteShareImage storedImage = this.routeMapSnapshotSupport.ensureSnapshot(
                authenticatedUser.id(),
                candidateSetId,
                completedRouteContext.execution(),
                completedRouteContext.route()
        );
        RouteSharePO share = this.routeShareManage.upsert(
                authenticatedUser.id(),
                candidateSetId,
                routeCode,
                normalizedShareText,
                storedImage.imageUrl(),
                storedImage.objectKey()
        );
        return this.toVO(share);
    }

    @Override
    public RouteGenerationVO getSharedRoute(UUID shareId) {
        RouteSharePO share = this.routeShareManage.findByShareId(shareId)
                .orElseThrow(() -> new IllegalArgumentException("分享路线不存在"));
        return this.routeGenerationHistoryManage
                .findRouteGenerationByUserAndCandidateSetId(share.getUserId(), share.getCandidateSetId())
                .orElseThrow(() -> new IllegalArgumentException("分享路线历史不存在"));
    }

    @Override
    public byte[] buildRouteStaticMap(AuthenticatedUser authenticatedUser, UUID candidateSetId, String routeCode) {
        RouteGenerationVO routeGeneration = this.routeGenerationHistoryManage
                .findRouteGenerationByUserAndCandidateSetId(authenticatedUser.id(), candidateSetId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        GeneratedRouteVO route = this.findRoute(routeGeneration, routeCode);
        return this.routeStaticMapImageBuilder.build(route);
    }

    private CompletedRouteContext validateCompletedRoute(UUID userId, UUID candidateSetId, String routeCode) {
        RouteExecutionPO execution = this.routeExecutionManage.findLatestByCandidateSetId(userId, candidateSetId)
                .orElseThrow(() -> new IllegalArgumentException("路线尚未完成，不能分享"));
        if (!routeCode.equals(execution.getRouteCode()) || execution.getExecutionStatus() != RouteExecutionStatus.COMPLETED) {
            throw new IllegalArgumentException("只能分享已经走完的路线");
        }
        RouteGenerationVO routeGeneration = this.routeGenerationHistoryManage
                .findRouteGenerationByUserAndCandidateSetId(userId, candidateSetId)
                .orElseThrow(() -> new IllegalArgumentException("路线历史不存在"));
        return new CompletedRouteContext(execution, this.findRoute(routeGeneration, routeCode));
    }

    private GeneratedRouteVO findRoute(RouteGenerationVO routeGeneration, String routeCode) {
        return routeGeneration.routes().stream()
                .filter(route -> route.routeCode().equals(routeCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("路线不属于当前历史记录"));
    }

    private RouteShareVO toVO(RouteSharePO share) {
        List<RouteGenerationHistoryPO> histories = this.routeGenerationHistoryManage
                .findByUserAndCandidateSetId(share.getUserId(), share.getCandidateSetId());
        RouteHistoryRouteSummaryVO summary = histories.isEmpty() ? null : this.findRouteSummary(histories, share.getRouteCode());
        return new RouteShareVO(
                share.getId(),
                share.getCandidateSetId(),
                share.getRouteCode(),
                summary == null ? "城市路线" : summary.title(),
                summary == null ? null : summary.cityName(),
                summary == null ? null : summary.totalDurationMinutes(),
                summary == null ? null : summary.totalDistanceMeters(),
                summary == null ? null : summary.stopCount(),
                share.getShareText(),
                share.getImageUrl(),
                share.getCreatedAt()
        );
    }

    private RouteHistoryRouteSummaryVO findRouteSummary(List<RouteGenerationHistoryPO> histories, String routeCode) {
        return this.routeGenerationHistoryManage.toRouteSummaries(histories).stream()
                .filter(summary -> summary.routeCode().equals(routeCode))
                .findFirst()
                .orElse(null);
    }

    private record CompletedRouteContext(RouteExecutionPO execution, GeneratedRouteVO route) {
    }

}
