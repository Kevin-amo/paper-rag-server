package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

/**
 * 审阅批次请求DTO，用于创建或更新审阅批次。
 */
public record ReviewBatchRequest(
        /** 批次名称，不能为空 */
        @NotBlank(message = "批次名称不能为空") String name,
        /** 批次描述 */
        String description,
        /** 批次状态 */
        String status,
        /** 批次开始时间 */
        OffsetDateTime startsAt,
        /** 批次结束时间 */
        OffsetDateTime endsAt
) {
}