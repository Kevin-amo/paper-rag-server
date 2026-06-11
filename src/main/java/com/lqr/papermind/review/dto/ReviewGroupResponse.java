package com.lqr.papermind.review.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审阅小组响应DTO，包含小组基本信息及成员和任务统计。
 */
public record ReviewGroupResponse(
        /** 小组唯一标识 */
        UUID id,
        /** 所属批次ID */
        UUID batchId,
        /** 小组名称 */
        String name,
        /** 组长用户ID */
        UUID leaderUserId,
        /** 组长用户名 */
        String leaderUsername,
        /** 组长显示名称 */
        String leaderDisplayName,
        /** 小组状态 */
        String status,
        /** 成员数量 */
        long memberCount,
        /** 关联任务数量 */
        long taskCount,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
}