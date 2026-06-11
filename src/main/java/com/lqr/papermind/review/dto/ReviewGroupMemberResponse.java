package com.lqr.papermind.review.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审阅小组成员响应DTO，包含成员的基本信息和加入状态。
 */
public record ReviewGroupMemberResponse(
        /** 成员记录唯一标识 */
        UUID id,
        /** 所属小组ID */
        UUID groupId,
        /** 成员用户ID */
        UUID userId,
        /** 用户名 */
        String username,
        /** 显示名称 */
        String displayName,
        /** 成员角色，如LEAD、MEMBER */
        String memberRole,
        /** 成员状态，如ACTIVE、REMOVED */
        String status,
        /** 加入时间 */
        OffsetDateTime joinedAt,
        /** 移除时间 */
        OffsetDateTime removedAt,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
}