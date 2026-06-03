package com.lqr.paperragserver.common.exception;

import com.lqr.paperragserver.common.api.ApiErrorResponse;
import com.lqr.paperragserver.literature.exception.LiteratureSearchException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器，将参数校验、认证失败和业务状态异常转换为统一 JSON 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理请求参数校验异常，提取首个字段错误信息并返回 400 响应。
     *
     * @param ex      参数校验异常
     * @param request 当前 HTTP 请求
     * @return 包含校验错误详情的 400 响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数不合法" : error.getDefaultMessage())
                .orElse("请求参数不合法");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    /**
     * 处理认证失败异常，返回 401 响应。
     *
     * @param ex      认证异常
     * @param request 当前 HTTP 请求
     * @return 包含认证错误详情的 401 响应
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", ex.getMessage(), request);
    }

    /**
     * 处理文献检索业务异常，按照异常自身携带的状态码和错误码返回响应。
     *
     * @param ex      文献检索异常
     * @param request 当前 HTTP 请求
     * @return 包含业务错误详情的响应
     */
    @ExceptionHandler(LiteratureSearchException.class)
    public ResponseEntity<ApiErrorResponse> handleLiteratureSearchException(LiteratureSearchException ex, HttpServletRequest request) {
        return build(ex.status(), ex.code(), ex.getMessage(), request);
    }

    /**
     * 处理 Spring 响应状态异常，将状态码和原因信息转换为统一错误响应。
     *
     * @param ex      响应状态异常
     * @param request 当前 HTTP 请求
     * @return 包含状态异常详情的响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() == null || ex.getReason().isBlank() ? status.getReasonPhrase() : ex.getReason();
        return build(status, status.name(), message, request);
    }

    /**
     * 处理所有未被其他处理器捕获的异常，记录错误日志并返回 500 响应。
     *
     * @param ex      未预期异常
     * @param request 当前 HTTP 请求
     * @return 包含内部错误详情的 500 响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API exception at {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "服务内部错误：" + ex.getMessage(), request);
    }

    /**
     * 根据状态码、错误码和提示信息构造统一错误响应体。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 提示信息
     * @param request 当前 HTTP 请求，用于提取请求路径
     * @return 包含统一错误响应体的 ResponseEntity
     */
    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), code, message, request.getRequestURI()));
    }
}