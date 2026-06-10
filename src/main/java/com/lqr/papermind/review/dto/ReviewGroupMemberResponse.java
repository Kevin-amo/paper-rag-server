package com.lqr.papermind.review.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewGroupMemberResponse(
        UUID id,
        UUID groupId,
        UUID userId,
        String username,
        String displayName,
        String memberRole,
        String status,
        OffsetDateTime joinedAt,
        OffsetDateTime removedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}