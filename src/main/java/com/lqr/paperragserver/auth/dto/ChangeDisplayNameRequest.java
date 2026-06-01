package com.lqr.paperragserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeDisplayNameRequest(
        @NotBlank(message = "昵称不能为空") String displayName
) {
}