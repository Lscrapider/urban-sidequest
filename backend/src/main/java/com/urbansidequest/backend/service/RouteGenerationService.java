package com.urbansidequest.backend.service;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;

public interface RouteGenerationService {

    RouteGenerationVO generate(AuthenticatedUser authenticatedUser, RouteGenerateParam generateParam);
}
