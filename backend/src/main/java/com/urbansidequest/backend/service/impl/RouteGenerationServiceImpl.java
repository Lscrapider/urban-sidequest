package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.config.RouteGenerationTaskExecutorConfig;
import com.urbansidequest.backend.converter.route.RouteGenerationConverter;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.handler.route.context.RouteGenerationContext;
import com.urbansidequest.backend.handler.route.pipeline.RouteGenerationPipeline;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import com.urbansidequest.backend.service.RouteGenerationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
public class RouteGenerationServiceImpl implements RouteGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteGenerationServiceImpl.class);

    private final RouteGenerationPipeline routeGenerationPipeline;

    private final RouteGenerationConverter routeGenerationConverter;

    private final RouteGenerationHistoryManage routeGenerationHistoryManage;

    private final TaskExecutor routeGenerationTaskExecutor;

    public RouteGenerationServiceImpl(
            RouteGenerationPipeline routeGenerationPipeline,
            RouteGenerationConverter routeGenerationConverter,
            RouteGenerationHistoryManage routeGenerationHistoryManage,
            @Qualifier(RouteGenerationTaskExecutorConfig.ROUTE_GENERATION_TASK_EXECUTOR) TaskExecutor routeGenerationTaskExecutor
    ) {
        this.routeGenerationPipeline = routeGenerationPipeline;
        this.routeGenerationConverter = routeGenerationConverter;
        this.routeGenerationHistoryManage = routeGenerationHistoryManage;
        this.routeGenerationTaskExecutor = routeGenerationTaskExecutor;
    }

    @Override
    public RouteGenerationVO generate(AuthenticatedUser authenticatedUser, RouteGenerateParam generateParam) {
        UUID candidateSetId = UUID.randomUUID();
        RouteGenerationContext context = new RouteGenerationContext(candidateSetId, candidateSetId, authenticatedUser.id(), generateParam);
        RouteGenerationVO pendingGeneration = this.routeGenerationConverter.toRouteGenerationVO(
                context,
                RouteRequestStatus.PENDING,
                "queued"
        );
        this.routeGenerationHistoryManage.createPendingHistory(pendingGeneration);
        try {
            this.routeGenerationTaskExecutor.execute(() -> {
                try {
                    this.routeGenerationPipeline.execute(context);
                } catch (RuntimeException exception) {
                    LOGGER.error("异步路线生成任务异常，requestId={}", context.getRequestId(), exception);
                }
            });
        } catch (TaskRejectedException exception) {
            this.routeGenerationHistoryManage.deletePendingHistory(candidateSetId, authenticatedUser.id());
            throw exception;
        }
        return pendingGeneration;
    }
}
