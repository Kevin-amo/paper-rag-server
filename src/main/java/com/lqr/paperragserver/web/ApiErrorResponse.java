package com.lqr.paperragserver.web;

import java.time.OffsetDateTime;

/**
 * 统一 API 错误响应体，用于前端稳定读取状态码、错误码和提示信息。
 */
public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path,
        OffsetDateTime timestamp
) {
    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(status, code, message, path, OffsetDateTime.now());
    }
}