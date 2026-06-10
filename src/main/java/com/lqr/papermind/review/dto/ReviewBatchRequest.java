package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public record ReviewBatchRequest(
        @NotBlank(message = "批次名称不能为空") String name,
        String description,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
) {
}