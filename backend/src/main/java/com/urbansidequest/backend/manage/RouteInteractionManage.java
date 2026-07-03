package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.enums.RouteInteractionReaction;
import com.urbansidequest.backend.domain.po.RouteInteractionPO;
import com.urbansidequest.backend.mapper.RouteInteractionMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RouteInteractionManage extends ServiceImpl<RouteInteractionMapper, RouteInteractionPO> {

    public List<RouteInteractionPO> findByUserId(UUID userId) {
        return this.baseMapper.selectByUserId(userId);
    }

    public void upsert(UUID userId, UUID candidateSetId, String routeCode, boolean favorite, RouteInteractionReaction reaction) {
        int updatedCount = this.baseMapper.upsertInteraction(userId, candidateSetId, routeCode, favorite, reaction);
        if (updatedCount < 1) {
            throw new IllegalStateException("路线互动状态保存失败");
        }
    }
}
