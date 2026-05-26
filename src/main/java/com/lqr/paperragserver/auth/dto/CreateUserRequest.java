package com.lqr.paperragserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        String displayName,
        String email,
        List<String> roles
) {
}