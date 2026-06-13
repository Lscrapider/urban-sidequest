package com.urbansidequest.backend.domain.vo;

import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import java.util.List;
import java.util.UUID;

public record RouteGenerationVO(
        UUID requestId,
        RouteRequestStatus status,
        RouteAreaVO area,
        List<GeneratedRouteVO> routes,
        List<String> warnings
) {
}
