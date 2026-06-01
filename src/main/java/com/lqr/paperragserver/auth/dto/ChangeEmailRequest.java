package com.lqr.paperragserver.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email,
        @NotBlank(message = "验证码不能为空") String emailCode
) {
}