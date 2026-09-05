package com.urbansidequest.backend.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RouteGenerationTaskExecutorConfig {

    public static final String ROUTE_GENERATION_TASK_EXECUTOR = "routeGenerationTaskExecutor";

    private static final int POOL_SIZE = 2;
    private static final int QUEUE_CAPACITY = 8;

    @Bean(name = ROUTE_GENERATION_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor routeGenerationTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(POOL_SIZE);
        taskExecutor.setMaxPoolSize(POOL_SIZE);
        taskExecutor.setQueueCapacity(QUEUE_CAPACITY);
        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        taskExecutor.setThreadNamePrefix("route-generation-");
        return taskExecutor;
    }
}
