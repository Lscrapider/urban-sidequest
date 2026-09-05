package com.urbansidequest.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.urbansidequest.backend.domain.vo.ErrorVO;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    @Test
    void returnsServiceUnavailableWithRecoverableChineseDetailAndRequestPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/routes/requests");

        ResponseEntity<ErrorVO> response = new GlobalExceptionHandler().handleTaskRejectedException(
                new TaskRejectedException("full"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getBody().detail()).contains("路线生成任务", "请稍后重试");
        assertThat(response.getBody().path()).isEqualTo("/api/routes/requests");
    }
}
