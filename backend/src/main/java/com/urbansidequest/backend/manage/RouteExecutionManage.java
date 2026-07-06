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

    public Optional<RouteExecutionPO> findLatestByCandidateSetId(UUID userId, UUID candidateSetId) {
        return Optional.ofNullable(this.baseMapper.selectLatestByCandidateSetId(userId, candidateSetId));
    }

    public Map<UUID, RouteExecutionPO> findLatestByCandidateSetIds(UUID userId, List<UUID> candidateSetIds) {
        if (candidateSetIds == null || candidateSetIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.baseMapper.selectLatestByCandidateSetIds(userId, candidateSetIds).stream()
                .collect(Collectors.toMap(RouteExecutionPO::getCandidateSetId, Function.identity()));
    }

    @Transactional
    public void activateRoute(UUID userId, UUID candidateSetId, String routeCode) {
        this.baseMapper.abandonInProgressByUserId(userId);
        int insertedCount = this.baseMapper.insertInProgress(userId, candidateSetId, routeCode);
        if (insertedCount == 0) {
            throw new IllegalArgumentException("路线执行记录创建失败");
        }
    }

    public void completeActiveRoute(UUID userId, UUID candidateSetId, String routeCode) {
        int updatedCount = this.baseMapper.completeInProgress(userId, candidateSetId, routeCode);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("当前路线未处于进行中");
        }
    }

    public void saveMapSnapshot(UUID userId, UUID candidateSetId, String routeCode, String mapSnapshotUrl, String mapSnapshotObjectKey) {
        int updatedCount = this.baseMapper.updateMapSnapshot(
                userId,
                candidateSetId,
                routeCode,
                mapSnapshotUrl,
                mapSnapshotObjectKey
        );
        if (updatedCount == 0) {
            throw new IllegalArgumentException("已完成路线不存在，无法保存地图快照");
        }
    }
}
