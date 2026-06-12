package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员重置密码请求体。
 *
 * @param password 新密码
 */
public record ResetPasswordRequest(@NotBlank(message = "密码不能为空") String password) {
}
