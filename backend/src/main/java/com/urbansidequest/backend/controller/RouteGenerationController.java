package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.service.RouteGenerationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteGenerationController {

    private final RouteGenerationService routeGenerationService;

    public RouteGenerationController(RouteGenerationService routeGenerationService) {
        this.routeGenerationService = routeGenerationService;
    }

    @PostMapping("/requests")
    public RouteGenerationVO generate(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody RouteGenerateParam generateParam
    ) {
        return this.routeGenerationService.generate(authenticatedUser, generateParam);
    }
}
