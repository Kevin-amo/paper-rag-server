package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 发送换绑邮箱验证码请求体。
 *
 * @param email 新邮箱地址
 */
public record ChangeEmailCodeRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email
) {
}
