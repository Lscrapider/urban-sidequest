package com.urbansidequest.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.enums.RoutePreferenceJudgeType;
import com.urbansidequest.backend.domain.enums.RoutePreferenceJudgmentStatus;
import com.urbansidequest.backend.domain.param.RoutePreferenceJudgmentParam;
import com.urbansidequest.backend.domain.vo.RoutePreferenceJudgmentVO;
import com.urbansidequest.backend.manage.RoutePreferenceJudgmentManage;
import com.urbansidequest.backend.manage.RoutePreferenceTrainingSampleManage;
import com.urbansidequest.backend.service.RoutePreferenceTrainingService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutePreferenceTrainingServiceImpl implements RoutePreferenceTrainingService {

    private static final BigDecimal REAL_USER_WEIGHT = BigDecimal.valueOf(1.00d);

    private static final BigDecimal HUMAN_ANNOTATOR_WEIGHT = BigDecimal.valueOf(0.70d);

    private static final BigDecimal LLM_SIM_USER_WEIGHT = BigDecimal.valueOf(0.60d);

    private static final BigDecimal HEURISTIC_JUDGE_WEIGHT = BigDecimal.valueOf(0.10d);

    private final ObjectMapper objectMapper;

    private final RoutePreferenceJudgmentManage routePreferenceJudgmentManage;

    private final RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage;

    public RoutePreferenceTrainingServiceImpl(
            ObjectMapper objectMapper,
            RoutePreferenceJudgmentManage routePreferenceJudgmentManage,
            RoutePreferenceTrainingSampleManage routePreferenceTrainingSampleManage
    ) {
        this.objectMapper = objectMapper;
        this.routePreferenceJudgmentManage = routePreferenceJudgmentManage;
        this.routePreferenceTrainingSampleManage = routePreferenceTrainingSampleManage;
    }

    @Override
    @Transactional
    public RoutePreferenceJudgmentVO saveJudgment(RoutePreferenceJudgmentParam param) {
        String rankingJson = this.writeJson(param.getRanking());
        String acceptedRouteCodesJson = this.writeJson(param.getAcceptedRouteCodes());
        String rejectedRouteCodesJson = this.writeJson(param.getRejectedRouteCodes());
        String reasonCodesJson = this.writeJson(param.getReasonCodes());
        UUID judgmentId = this.routePreferenceJudgmentManage.saveCompletedJudgment(
                param.getCandidateSetId(),
                param.getJudgeType().name(),
                param.getJudgeModel(),
                param.getJudgePromptVersion(),
                rankingJson,
                acceptedRouteCodesJson,
                rejectedRouteCodesJson,
                reasonCodesJson,
                param.getConfidence()
        );

        String labelJson = this.writeJson(this.labelPayload(judgmentId, param));
        BigDecimal sampleWeight = this.sourceWeight(param.getJudgeType());
        this.routePreferenceTrainingSampleManage.markCandidateSetTrainReady(
                param.getCandidateSetId(),
                param.getJudgeType().name(),
                labelJson,
                sampleWeight
        );

        return new RoutePreferenceJudgmentVO(
                judgmentId,
                param.getCandidateSetId(),
                RoutePreferenceJudgmentStatus.COMPLETED.name()
        );
    }

    private Map<String, Object> labelPayload(UUID judgmentId, RoutePreferenceJudgmentParam param) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("judgmentId", judgmentId);
        payload.put("source", param.getJudgeType());
        payload.put("judgeModel", param.getJudgeModel());
        payload.put("judgePromptVersion", param.getJudgePromptVersion());
        payload.put("ranking", param.getRanking());
        payload.put("acceptedRouteCodes", param.getAcceptedRouteCodes());
        payload.put("rejectedRouteCodes", param.getRejectedRouteCodes());
        payload.put("reasonCodes", param.getReasonCodes());
        payload.put("confidence", param.getConfidence());
        return payload;
    }

    private BigDecimal sourceWeight(RoutePreferenceJudgeType judgeType) {
        return switch (judgeType) {
            case REAL_USER -> REAL_USER_WEIGHT;
            case HUMAN_ANNOTATOR -> HUMAN_ANNOTATOR_WEIGHT;
            case LLM_SIM_USER -> LLM_SIM_USER_WEIGHT;
            case HEURISTIC_JUDGE -> HEURISTIC_JUDGE_WEIGHT;
        };
    }

    private String writeJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线偏好反馈序列化失败", exception);
        }
    }
}
