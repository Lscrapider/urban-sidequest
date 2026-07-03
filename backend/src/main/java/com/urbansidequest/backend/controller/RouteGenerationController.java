package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.RouteActiveParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryGroupVO;
import com.urbansidequest.backend.service.RouteGenerationService;
import com.urbansidequest.backend.service.RouteHistoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteGenerationController {

    private final RouteGenerationService routeGenerationService;

    private final RouteHistoryService routeHistoryService;

    public RouteGenerationController(
            RouteGenerationService routeGenerationService,
            RouteHistoryService routeHistoryService
    ) {
        this.routeGenerationService = routeGenerationService;
        this.routeHistoryService = routeHistoryService;
    }

    @PostMapping("/requests")
    public RouteGenerationVO generate(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody RouteGenerateParam generateParam
    ) {
        return this.routeGenerationService.generate(authenticatedUser, generateParam);
    }

    @GetMapping("/history")
    public List<RouteHistoryGroupVO> history(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return this.routeHistoryService.listHistory(authenticatedUser, pageNum, pageSize);
    }

    @GetMapping("/history/{requestId}")
    public RouteGenerationVO historyDetail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID requestId
    ) {
        return this.routeHistoryService.getHistoryDetail(authenticatedUser, requestId);
    }

    @GetMapping("/active")
    public RouteGenerationVO active(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return this.routeHistoryService.getActiveRoute(authenticatedUser);
    }

    @PostMapping("/history/{requestId}/active-route")
    public RouteGenerationVO activateRoute(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID requestId,
            @Valid @RequestBody RouteActiveParam param
    ) {
        return this.routeHistoryService.activateRoute(authenticatedUser, requestId, param);
    }

    @PostMapping("/history/{requestId}/active-route/complete")
    public RouteGenerationVO completeActiveRoute(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID requestId,
            @Valid @RequestBody RouteActiveParam param
    ) {
        return this.routeHistoryService.completeActiveRoute(authenticatedUser, requestId, param);
    }
}
