package com.lqr.paperragserver.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email,
        @NotBlank(message = "验证码不能为空") @Pattern(regexp = "\\d{6}", message = "验证码必须是6位数字") String emailCode
) {
}