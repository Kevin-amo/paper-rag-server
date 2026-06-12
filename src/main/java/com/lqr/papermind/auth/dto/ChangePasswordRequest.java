package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码请求体。
 *
 * @param currentPassword 当前密码
 * @param newPassword 新密码
 */
public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空") String currentPassword,
        @NotBlank(message = "新密码不能为空") String newPassword
) {
}
