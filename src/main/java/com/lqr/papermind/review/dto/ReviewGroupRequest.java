package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 审阅小组创建/更新请求DTO，包含小组名称、所属批次及组长信息。
 */
public record ReviewGroupRequest(
        /** 所属批次ID，不能为空 */
        @NotNull(message = "批次不能为空") UUID batchId,
        /** 小组名称，不能为空 */
        @NotBlank(message = "小组名称不能为空") String name,
        /** 组长用户ID，不能为空 */
        @NotNull(message = "组长不能为空") UUID leaderUserId,
        /** 小组状态 */
        String status
) {
}