package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReviewGroupMemberUpdateRequest(
        @NotNull(message = "组长不能为空") UUID leaderUserId,
        List<UUID> memberUserIds
) {
}