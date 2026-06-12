package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 用户注册请求体。
 *
 * @param username 用户名
 * @param password 密码
 * @param email 邮箱地址
 * @param emailCode 邮箱验证码（6位数字）
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不合法") String email,
        @NotBlank(message = "验证码不能为空") @Pattern(regexp = "\\d{6}", message = "验证码必须是6位数字") String emailCode
) {
}
