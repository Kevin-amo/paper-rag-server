package com.lqr.papermind.common.api;

import java.time.OffsetDateTime;

/**
 * 统一 API 错误响应体，用于前端稳定读取状态码、错误码和提示信息。
 *
 * @param status HTTP 状态码
 * @param code 业务错误码
 * @param message 提示信息
 * @param path 请求路径
 * @param timestamp 错误发生时间
 */
public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path,
        OffsetDateTime timestamp
) {
    /**
     * 根据状态码、错误码、提示信息和请求路径构造错误响应，时间戳自动取当前时刻。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 提示信息
     * @param path    请求路径
     * @return 统一错误响应体
     */
    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(status, code, message, path, OffsetDateTime.now());
    }
}