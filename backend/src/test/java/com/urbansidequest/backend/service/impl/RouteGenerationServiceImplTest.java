package com.urbansidequest.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.converter.route.RouteGenerationConverter;
import com.urbansidequest.backend.domain.enums.AreaMode;
import com.urbansidequest.backend.domain.enums.RouteGoal;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.enums.TransportProfile;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.handler.route.linear.PoiSemanticResolver;
import com.urbansidequest.backend.handler.route.pipeline.RouteGenerationPipeline;
import com.urbansidequest.backend.manage.RouteGenerationHistoryManage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class RouteGenerationServiceImplTest {

    @Test
    void acceptsGenerationBeforeQueuedPipelineRuns() {
        CapturingTaskExecutor taskExecutor = new CapturingTaskExecutor();
        Fixture fixture = fixture(taskExecutor);

        var accepted = fixture.service().generate(fixture.user(), fixture.param());

        assertThat(accepted.status()).isEqualTo(RouteRequestStatus.PENDING);
        assertThat(accepted.generationStage()).isEqualTo("queued");
        assertThat(accepted.routes()).isEmpty();
        assertThat(taskExecutor.submittedTask()).isNotNull();
        verify(fixture.historyManage()).createPendingHistory(accepted);
        verifyNoInteractions(fixture.pipeline());
    }

    @Test
    void removesPendingHistoryWhenExecutorRejectsTask() {
        Fixture fixture = fixture(task -> {
            throw new TaskRejectedException("full");
        });

        assertThatThrownBy(() -> fixture.service().generate(fixture.user(), fixture.param()))
                .isInstanceOf(TaskRejectedException.class);

        verify(fixture.historyManage()).createPendingHistory(any());
        verify(fixture.historyManage()).deletePendingHistory(any(UUID.class), eq(fixture.user().id()));
    }

    private static Fixture fixture(TaskExecutor taskExecutor) {
        RouteGenerationPipeline pipeline = mock(RouteGenerationPipeline.class);
        RouteGenerationHistoryManage historyManage = mock(RouteGenerationHistoryManage.class);
        RouteGenerationConverter converter = new RouteGenerationConverter(mock(PoiSemanticResolver.class));
        RouteGenerationServiceImpl service = new RouteGenerationServiceImpl(
                pipeline,
                converter,
                historyManage,
                taskExecutor
        );
        return new Fixture(service, pipeline, historyManage, user(), param());
    }

    private static AuthenticatedUser user() {
        return new AuthenticatedUser(UUID.randomUUID(), "13800138000");
    }

    private static RouteGenerateParam param() {
        RouteGenerateParam param = new RouteGenerateParam();
        param.setAreaMode(AreaMode.AUTO_RADIUS);
        param.setRouteCityName("北京市");
        param.setDepartureTime(LocalDateTime.of(2026, 9, 5, 9, 0));
        param.setDurationMinutes(240);
        param.setTransportProfile(TransportProfile.WALK_ONLY);
        param.setRouteGoal(RouteGoal.CLASSIC);
        return param;
    }

    private record Fixture(
            RouteGenerationServiceImpl service,
            RouteGenerationPipeline pipeline,
            RouteGenerationHistoryManage historyManage,
            AuthenticatedUser user,
            RouteGenerateParam param
    ) {
    }

    private static final class CapturingTaskExecutor implements TaskExecutor {

        private Runnable submittedTask;

        @Override
        public void execute(Runnable task) {
            this.submittedTask = task;
        }

        private Runnable submittedTask() {
            return this.submittedTask;
        }
    }
}
