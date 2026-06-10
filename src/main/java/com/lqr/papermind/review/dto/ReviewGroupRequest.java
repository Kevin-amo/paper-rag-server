package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReviewGroupRequest(
        @NotNull(message = "批次不能为空") UUID batchId,
        @NotBlank(message = "小组名称不能为空") String name,
        @NotNull(message = "组长不能为空") UUID leaderUserId,
        String status
) {
}