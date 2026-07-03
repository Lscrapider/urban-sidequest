package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.domain.enums.RoutePreferenceJudgmentStatus;
import com.urbansidequest.backend.domain.param.RoutePreferenceJudgmentParam;
import com.urbansidequest.backend.domain.vo.RoutePreferenceJudgmentVO;
import com.urbansidequest.backend.handler.route.training.RoutePreferenceJudgmentIngestPayload;
import com.urbansidequest.backend.provider.route.training.RoutePreferenceTrainingObjectStore;
import com.urbansidequest.backend.service.RoutePreferenceTrainingService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RoutePreferenceTrainingServiceImpl implements RoutePreferenceTrainingService {

    private final RoutePreferenceTrainingObjectStore routePreferenceTrainingObjectStore;

    public RoutePreferenceTrainingServiceImpl(RoutePreferenceTrainingObjectStore routePreferenceTrainingObjectStore) {
        this.routePreferenceTrainingObjectStore = routePreferenceTrainingObjectStore;
    }

    @Override
    public RoutePreferenceJudgmentVO saveJudgment(RoutePreferenceJudgmentParam param) {
        UUID judgmentId = UUID.randomUUID();
        this.routePreferenceTrainingObjectStore.writeJudgment(
                RoutePreferenceJudgmentIngestPayload.completed(judgmentId, param)
        );

        return new RoutePreferenceJudgmentVO(
                judgmentId,
                param.getCandidateSetId(),
                RoutePreferenceJudgmentStatus.COMPLETED.name()
        );
    }
}
