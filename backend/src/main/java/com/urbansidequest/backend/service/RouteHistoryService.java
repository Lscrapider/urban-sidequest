package com.urbansidequest.backend.service;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.RouteActiveParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.domain.vo.RouteHistoryGroupVO;
import java.util.List;
import java.util.UUID;

public interface RouteHistoryService {

    List<RouteHistoryGroupVO> listHistory(AuthenticatedUser authenticatedUser, int pageNum, int pageSize);

    RouteGenerationVO getHistoryDetail(AuthenticatedUser authenticatedUser, UUID candidateSetId);

    RouteGenerationVO getActiveRoute(AuthenticatedUser authenticatedUser);

    RouteGenerationVO activateRoute(AuthenticatedUser authenticatedUser, UUID candidateSetId, RouteActiveParam param);

    RouteGenerationVO completeActiveRoute(AuthenticatedUser authenticatedUser, UUID candidateSetId, RouteActiveParam param);
}
