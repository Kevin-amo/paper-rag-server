package com.lqr.papermind.review.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 管理员审阅任务摘要响应DTO，提供任务概览信息用于管理后台展示。
 */
public record AdminReviewTaskSummaryResponse(
        /** 任务唯一标识 */
        UUID id,
        /** 关联文档ID */
        UUID documentId,
        /** 提交者用户ID */
        UUID submitterUserId,
        /** 论文来源标识 */
        String sourceId,
        /** 论文标题 */
        String title,
        /** 任务状态 */
        String status,
        /** 已分配的审阅者数量 */
        long assignmentCount,
        /** 已提交报告的数量 */
        long submittedCount,
        /** 主审阅者（组长）用户ID */
        UUID leadReviewerUserId,
        /** 主审阅者用户名 */
        String leadReviewerUsername,
        /** 主审阅者显示名称 */
        String leadReviewerDisplayName,
        /** 截止时间 */
        OffsetDateTime dueAt,
        /** 共识状态，如PENDING、CONFIRMED等 */
        String consensusStatus,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
}
