package com.lqr.paperragserver.review.dto;

import java.util.UUID;

public record ReviewerLoadResponse(
        UUID reviewerUserId,
        String username,
        String displayName,
        long assignedCount,
        long reviewingCount,
        long submittedCount
) {
}
