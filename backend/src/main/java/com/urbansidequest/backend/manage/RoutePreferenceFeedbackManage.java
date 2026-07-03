package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.enums.RoutePreferenceFeedbackLabel;
import com.urbansidequest.backend.domain.po.RoutePreferenceFeedbackPO;
import com.urbansidequest.backend.mapper.RoutePreferenceFeedbackMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoutePreferenceFeedbackManage extends ServiceImpl<RoutePreferenceFeedbackMapper, RoutePreferenceFeedbackPO> {

    public void upsert(UUID userId, UUID candidateSetId, String routeCode, RoutePreferenceFeedbackLabel feedbackLabel) {
        int updatedCount = this.baseMapper.upsertFeedback(userId, candidateSetId, routeCode, feedbackLabel);
        if (updatedCount < 1) {
            throw new IllegalStateException("路线偏好反馈保存失败");
        }
    }
}
