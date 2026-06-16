package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.converter.route.RouteGenerationConverter;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.pipeline.RouteGenerationPipeline;
import com.urbansidequest.backend.service.RouteGenerationService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RouteGenerationServiceImpl implements RouteGenerationService {

    private final RouteGenerationPipeline routeGenerationPipeline;

    private final RouteGenerationConverter routeGenerationConverter;

    public RouteGenerationServiceImpl(
            RouteGenerationPipeline routeGenerationPipeline,
            RouteGenerationConverter routeGenerationConverter
    ) {
        this.routeGenerationPipeline = routeGenerationPipeline;
        this.routeGenerationConverter = routeGenerationConverter;
    }

    @Override
    public RouteGenerationVO generate(AuthenticatedUser authenticatedUser, RouteGenerateParam generateParam) {
        RouteGenerationContext context = new RouteGenerationContext(UUID.randomUUID(), authenticatedUser.id(), generateParam);
        this.routeGenerationPipeline.execute(context);
        return this.routeGenerationConverter.toRouteGenerationVO(context);
    }
}
