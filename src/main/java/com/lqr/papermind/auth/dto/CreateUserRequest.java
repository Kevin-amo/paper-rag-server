package com.lqr.papermind.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 管理员创建用户请求体。
 *
 * @param username 用户名
 * @param password 密码
 * @param displayName 昵称
 * @param email 邮箱地址
 * @param roles 角色编码列表
 */
public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        String displayName,
        String email,
        List<String> roles
) {
}
