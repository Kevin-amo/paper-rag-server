package com.lqr.papermind.review.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewGroupResponse(
        UUID id,
        UUID batchId,
        String name,
        UUID leaderUserId,
        String leaderUsername,
        String leaderDisplayName,
        String status,
        long memberCount,
        long taskCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}