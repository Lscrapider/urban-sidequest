package com.urbansidequest.backend.service;

import com.urbansidequest.backend.domain.param.RoutePreferenceJudgmentParam;
import com.urbansidequest.backend.domain.vo.RoutePreferenceJudgmentVO;

public interface RoutePreferenceTrainingService {

    RoutePreferenceJudgmentVO saveJudgment(RoutePreferenceJudgmentParam param);
}
