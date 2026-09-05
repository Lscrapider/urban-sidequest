package com.urbansidequest.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class RouteGenerationTaskExecutorConfigTest {

    private static final int RUNNING_TASK_COUNT = 2;
    private static final int QUEUED_TASK_COUNT = 8;

    @Test
    void rejectsEleventhTaskAfterTwoRunningTasksAndEightQueuedTasks() throws InterruptedException {
        ThreadPoolTaskExecutor taskExecutor = new RouteGenerationTaskExecutorConfig().routeGenerationTaskExecutor();
        CountDownLatch tasksStarted = new CountDownLatch(RUNNING_TASK_COUNT);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        taskExecutor.initialize();

        try {
            Runnable blockingTask = () -> {
                tasksStarted.countDown();
                try {
                    releaseTasks.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            };
            taskExecutor.execute(blockingTask);
            taskExecutor.execute(blockingTask);

            assertThat(tasksStarted.await(5, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < QUEUED_TASK_COUNT; index++) {
                taskExecutor.execute(() -> {
                });
            }

            assertThatThrownBy(() -> taskExecutor.execute(() -> {
            })).isInstanceOf(TaskRejectedException.class);
        } finally {
            releaseTasks.countDown();
            taskExecutor.shutdown();
        }
    }
}
