package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.po.RouteExecutionPO;
import com.urbansidequest.backend.mapper.RouteExecutionMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RouteExecutionManage extends ServiceImpl<RouteExecutionMapper, RouteExecutionPO> {

    public Optional<RouteExecutionPO> findInProgressByUserId(UUID userId) {
        return Optional.ofNullable(this.baseMapper.selectInProgressByUserId(userId));
    }

    public Optional<RouteExecutionPO> findLatestByRequestId(UUID userId, UUID requestId) {
        return Optional.ofNullable(this.baseMapper.selectLatestByRequestId(userId, requestId));
    }

    public Map<UUID, RouteExecutionPO> findLatestByRequestIds(UUID userId, List<UUID> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.baseMapper.selectLatestByRequestIds(userId, requestIds).stream()
                .collect(Collectors.toMap(RouteExecutionPO::getRequestId, Function.identity()));
    }

    @Transactional
    public void activateRoute(UUID userId, UUID requestId, String routeCode) {
        this.baseMapper.abandonInProgressByUserId(userId);
        int insertedCount = this.baseMapper.insertInProgress(userId, requestId, routeCode);
        if (insertedCount == 0) {
            throw new IllegalArgumentException("路线执行记录创建失败");
        }
    }

    public void completeActiveRoute(UUID userId, UUID requestId, String routeCode) {
        int updatedCount = this.baseMapper.completeInProgress(userId, requestId, routeCode);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("当前路线未处于进行中");
        }
    }
}
