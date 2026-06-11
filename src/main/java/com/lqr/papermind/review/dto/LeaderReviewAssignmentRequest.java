package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 组长分配审阅任务的请求DTO，用于组长将任务分配给组内成员。
 */
public record LeaderReviewAssignmentRequest(
        /** 被分配的审阅者用户ID列表，不能为空 */
        @NotEmpty List<UUID> reviewerUserIds,
        /** 截止时间 */
        OffsetDateTime dueAt
) {
}