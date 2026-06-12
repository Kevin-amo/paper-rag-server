package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 发送注册邮箱验证码请求体。
 *
 * @param email 邮箱地址
 */
public record RegisterEmailCodeRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email
) {
}
