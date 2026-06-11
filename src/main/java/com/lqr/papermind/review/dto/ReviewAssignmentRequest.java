package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审阅任务分配请求DTO，用于将审阅任务分配给指定审阅者。
 */
public record ReviewAssignmentRequest(
        /** 被分配的审阅者用户ID列表，不能为空 */
        @NotEmpty List<UUID> reviewerUserIds,
        /** 主审阅者（负责人）用户ID，不能为空 */
        @NotNull UUID leadReviewerUserId,
        /** 截止时间 */
        OffsetDateTime dueAt
) {
}
