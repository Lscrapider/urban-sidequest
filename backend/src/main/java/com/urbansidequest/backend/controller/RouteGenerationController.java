package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.RouteActiveParam;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.param.RouteInteractionParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryGroupVO;
import com.urbansidequest.backend.domain.vo.RouteInteractionVO;
import com.urbansidequest.backend.service.RouteGenerationService;
import com.urbansidequest.backend.service.RouteHistoryService;
import com.urbansidequest.backend.service.RouteInteractionService;
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
    private final RouteInteractionService routeInteractionService;

    public RouteGenerationController(
            RouteGenerationService routeGenerationService,
            RouteHistoryService routeHistoryService,
            RouteInteractionService routeInteractionService
    ) {
        this.routeGenerationService = routeGenerationService;
        this.routeHistoryService = routeHistoryService;
        this.routeInteractionService = routeInteractionService;
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

    @GetMapping("/interactions")
    public List<RouteInteractionVO> interactions(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return this.routeInteractionService.listInteractions(authenticatedUser);
    }

    @GetMapping("/history/{requestId}")
    public RouteGenerationVO historyDetail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable("requestId") UUID candidateSetId
    ) {
        return this.routeHistoryService.getHistoryDetail(authenticatedUser, candidateSetId);
    }

    @GetMapping("/active")
    public RouteGenerationVO active(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return this.routeHistoryService.getActiveRoute(authenticatedUser);
    }

    @PostMapping("/history/{requestId}/active-route")
    public RouteGenerationVO activateRoute(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable("requestId") UUID candidateSetId,
            @Valid @RequestBody RouteActiveParam param
    ) {
        return this.routeHistoryService.activateRoute(authenticatedUser, candidateSetId, param);
    }

    @PostMapping("/history/{requestId}/active-route/complete")
    public RouteGenerationVO completeActiveRoute(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable("requestId") UUID candidateSetId,
            @Valid @RequestBody RouteActiveParam param
    ) {
        return this.routeHistoryService.completeActiveRoute(authenticatedUser, candidateSetId, param);
    }

    @PostMapping("/history/{requestId}/routes/{routeCode}/interaction")
    public RouteInteractionVO saveInteraction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable("requestId") UUID candidateSetId,
            @PathVariable String routeCode,
            @Valid @RequestBody RouteInteractionParam param
    ) {
        return this.routeInteractionService.saveInteraction(authenticatedUser, candidateSetId, routeCode, param);
    }
}
