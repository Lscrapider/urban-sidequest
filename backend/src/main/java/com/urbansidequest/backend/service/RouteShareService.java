package com.urbansidequest.backend.service;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteShareVO;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface RouteShareService {

    List<RouteShareVO> listLatestShares(int pageSize);

    RouteShareVO shareCompletedRoute(
            AuthenticatedUser authenticatedUser,
            UUID requestId,
            String routeCode,
            String shareText,
            MultipartFile image
    );

    RouteGenerationVO getSharedRoute(UUID shareId);

    byte[] buildRouteStaticMap(AuthenticatedUser authenticatedUser, UUID requestId, String routeCode);
}
