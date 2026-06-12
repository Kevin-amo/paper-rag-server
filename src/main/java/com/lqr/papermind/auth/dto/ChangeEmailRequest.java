package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 换绑邮箱请求体。
 *
 * @param email 新邮箱地址
 * @param emailCode 邮箱验证码
 */
public record ChangeEmailRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email,
        @NotBlank(message = "验证码不能为空") String emailCode
) {
}
