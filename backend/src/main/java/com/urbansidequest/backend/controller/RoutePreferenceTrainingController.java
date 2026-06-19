package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.domain.param.RoutePreferenceJudgmentParam;
import com.urbansidequest.backend.domain.vo.RoutePreferenceJudgmentVO;
import com.urbansidequest.backend.service.RoutePreferenceTrainingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/route-preferences")
public class RoutePreferenceTrainingController {

    private final RoutePreferenceTrainingService routePreferenceTrainingService;

    public RoutePreferenceTrainingController(RoutePreferenceTrainingService routePreferenceTrainingService) {
        this.routePreferenceTrainingService = routePreferenceTrainingService;
    }

    @PostMapping("/judgments")
    public RoutePreferenceJudgmentVO saveJudgment(@Valid @RequestBody RoutePreferenceJudgmentParam param) {
        return this.routePreferenceTrainingService.saveJudgment(param);
    }
}
