package com.urbansidequest.backend.manage;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.urbansidequest.backend.domain.enums.RoutePreferenceJudgmentStatus;
import com.urbansidequest.backend.domain.po.RoutePreferenceJudgmentPO;
import com.urbansidequest.backend.mapper.RoutePreferenceJudgmentMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RoutePreferenceJudgmentManage extends ServiceImpl<RoutePreferenceJudgmentMapper, RoutePreferenceJudgmentPO> {

    public UUID saveCompletedJudgment(
            UUID candidateSetId,
            String judgeType,
            String judgeModel,
            String judgePromptVersion,
            String rankingJson,
            String acceptedRouteCodesJson,
            String rejectedRouteCodesJson,
            String reasonCodesJson,
            BigDecimal confidence
    ) {
        UUID judgmentId = UUID.randomUUID();
        this.baseMapper.insertJudgment(
                judgmentId,
                candidateSetId,
                judgeType,
                judgeModel,
                judgePromptVersion,
                rankingJson,
                acceptedRouteCodesJson,
                rejectedRouteCodesJson,
                reasonCodesJson,
                confidence,
                RoutePreferenceJudgmentStatus.COMPLETED.name()
        );
        return judgmentId;
    }
}
