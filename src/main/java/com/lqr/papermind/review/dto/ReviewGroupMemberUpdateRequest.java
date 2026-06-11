package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 审阅小组成员更新请求DTO，用于更新小组的组长和成员列表。
 */
public record ReviewGroupMemberUpdateRequest(
        /** 组长用户ID，不能为空 */
        @NotNull(message = "组长不能为空") UUID leaderUserId,
        /** 成员用户ID列表 */
        List<UUID> memberUserIds
) {
}