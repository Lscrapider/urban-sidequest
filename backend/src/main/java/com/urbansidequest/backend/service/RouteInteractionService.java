package com.urbansidequest.backend.service;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.RouteInteractionParam;
import com.urbansidequest.backend.domain.vo.RouteInteractionVO;
import java.util.List;
import java.util.UUID;

public interface RouteInteractionService {

    List<RouteInteractionVO> listInteractions(AuthenticatedUser authenticatedUser);

    RouteInteractionVO saveInteraction(
            AuthenticatedUser authenticatedUser,
            UUID candidateSetId,
            String routeCode,
            RouteInteractionParam param
    );
}
