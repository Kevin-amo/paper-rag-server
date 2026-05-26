package com.lqr.paperragserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank(message = "密码不能为空") String password) {
}