package com.urbansidequest.backend.service;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteShareVO;
import java.util.List;
import java.util.UUID;

public interface RouteShareService {

    List<RouteShareVO> listLatestShares(int pageSize);

    RouteShareVO shareCompletedRoute(
            AuthenticatedUser authenticatedUser,
            UUID candidateSetId,
            String routeCode,
            String shareText
    );

    RouteGenerationVO getSharedRoute(UUID shareId);

    byte[] buildRouteStaticMap(AuthenticatedUser authenticatedUser, UUID candidateSetId, String routeCode);
}
