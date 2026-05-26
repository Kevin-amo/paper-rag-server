package com.lqr.paperragserver.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(@NotBlank(message = "状态不能为空") String status) {
}