package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteShareVO;
import com.urbansidequest.backend.service.RouteShareService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteShareController {

    private final RouteShareService routeShareService;

    public RouteShareController(RouteShareService routeShareService) {
        this.routeShareService = routeShareService;
    }

    @GetMapping("/shares")
    public List<RouteShareVO> shares(@RequestParam(defaultValue = "20") int pageSize) {
        return this.routeShareService.listLatestShares(pageSize);
    }

    @GetMapping("/shares/{shareId}/route")
    public RouteGenerationVO sharedRoute(@PathVariable UUID shareId) {
        return this.routeShareService.getSharedRoute(shareId);
    }

    @GetMapping(
            value = "/history/{requestId}/routes/{routeCode}/share-preview-map",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> routeSharePreviewMap(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable("requestId") UUID candidateSetId,
            @PathVariable String routeCode
    ) {
        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(this.routeShareService.buildRouteStaticMap(authenticatedUser, candidateSetId, routeCode));
    }

    @PostMapping("/history/{requestId}/routes/{routeCode}/share")
    public RouteShareVO shareCompletedRoute(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable("requestId") UUID candidateSetId,
            @PathVariable String routeCode,
            @RequestParam("shareText") String shareText
    ) {
        return this.routeShareService.shareCompletedRoute(authenticatedUser, candidateSetId, routeCode, shareText);
    }
}
