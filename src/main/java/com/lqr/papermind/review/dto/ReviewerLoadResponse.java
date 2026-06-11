package com.lqr.papermind.review.dto;

import java.util.UUID;

/**
 * 审阅者工作负载响应DTO，展示审阅者的分配、审阅中和已提交任务数量。
 */
public record ReviewerLoadResponse(
        /** 审阅者用户ID */
        UUID reviewerUserId,
        /** 用户名 */
        String username,
        /** 显示名称 */
        String displayName,
        /** 已分配的任务数量 */
        long assignedCount,
        /** 审阅中的任务数量 */
        long reviewingCount,
        /** 已提交的任务数量 */
        long submittedCount
) {
}
