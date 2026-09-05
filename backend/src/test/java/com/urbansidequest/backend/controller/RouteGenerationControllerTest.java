package com.urbansidequest.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.enums.RouteExecutionStatus;
import com.urbansidequest.backend.domain.enums.RouteRequestStatus;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import com.urbansidequest.backend.domain.vo.RouteGenerationVO;
import com.urbansidequest.backend.service.RouteGenerationService;
import com.urbansidequest.backend.service.RouteHistoryService;
import com.urbansidequest.backend.service.RouteInteractionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RouteGenerationControllerTest {

    @Test
    void returnsAcceptedForPendingGeneration() {
        RouteGenerationService routeGenerationService = mock(RouteGenerationService.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "13800138000");
        RouteGenerateParam param = new RouteGenerateParam();
        RouteGenerationVO pending = new RouteGenerationVO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                user.id(),
                RouteRequestStatus.PENDING,
                null,
                List.of(),
                List.of(),
                "queued",
                null,
                RouteExecutionStatus.GENERATED
        );
        when(routeGenerationService.generate(user, param)).thenReturn(pending);
        RouteGenerationController controller = new RouteGenerationController(
                routeGenerationService,
                mock(RouteHistoryService.class),
                mock(RouteInteractionService.class)
        );

        ResponseEntity<RouteGenerationVO> response = controller.generate(user, param);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(pending);
    }
}
