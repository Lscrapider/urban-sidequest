package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.po.RouteGenerationHistoryPO;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.mapper.RouteGenerationHistoryMapper;
import java.util.List;
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

    public void upsertHistory(RouteGenerationVO routeGeneration) {
        RouteGenerationHistoryPO history = RouteGenerationHistoryPO.fromRouteGeneration(routeGeneration, this.objectMapper);
        this.baseMapper.upsertHistory(history);
    }

    public List<RouteGenerationHistoryPO> findByUserId(UUID userId, int pageSize, int offset) {
        return this.baseMapper.selectByUserId(userId, pageSize, offset);
    }

    public Optional<RouteGenerationHistoryPO> findByUserAndRequestId(UUID userId, UUID requestId) {
        return Optional.ofNullable(this.baseMapper.selectByUserAndRequestId(userId, requestId));
    }

    public Optional<RouteGenerationHistoryPO> findActiveByUserId(UUID userId) {
        return Optional.ofNullable(this.baseMapper.selectActiveByUserId(userId));
    }

    @Transactional
    public void activateRoute(UUID userId, UUID requestId, String routeCode) {
        this.baseMapper.clearInProgressByUserId(userId);
        int updatedCount = this.baseMapper.setActiveRoute(userId, requestId, routeCode);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("路线历史不存在");
        }
    }

    public RouteGenerationVO toRouteGenerationVO(RouteGenerationHistoryPO history) {
        return history.toRouteGenerationVO(this.objectMapper);
    }
}
