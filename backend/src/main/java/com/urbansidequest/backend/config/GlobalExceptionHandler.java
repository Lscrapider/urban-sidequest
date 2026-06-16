package com.urbansidequest.backend.config;

import com.urbansidequest.backend.domain.vo.ErrorVO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorVO> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("请求参数校验失败：{}", request.getRequestURI(), exception);
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数不合法");
        return this.buildResponse(HttpStatus.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorVO> handleIllegalArgumentException(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("请求参数非法：{}", request.getRequestURI(), exception);
        return this.buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorVO> handleIllegalStateException(
            IllegalStateException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("业务状态异常：{}", request.getRequestURI(), exception);
        return this.buildResponse(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorVO> handleDataAccessException(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        LOGGER.error("数据库访问失败：{}", request.getRequestURI(), exception);
        return this.buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "数据库访问失败，请检查服务配置", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorVO> handleException(Exception exception, HttpServletRequest request) {
        LOGGER.error("服务异常：{}", request.getRequestURI(), exception);
        return this.buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务异常，请稍后重试", request);
    }

    private ResponseEntity<ErrorVO> buildResponse(HttpStatus status, String detail, HttpServletRequest request) {
        ErrorVO error = new ErrorVO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                detail == null || detail.isBlank() ? "请求处理失败" : detail,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(error);
    }
}
