package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新用户状态请求体。
 *
 * @param status 新状态（ACTIVE 或 DISABLED）
 */
public record UpdateStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}
