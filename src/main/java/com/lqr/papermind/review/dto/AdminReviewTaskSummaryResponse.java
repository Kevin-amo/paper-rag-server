package com.lqr.papermind.review.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminReviewTaskSummaryResponse(
        UUID id,
        UUID documentId,
        UUID submitterUserId,
        String sourceId,
        String title,
        String status,
        long assignmentCount,
        long submittedCount,
        UUID leadReviewerUserId,
        String leadReviewerUsername,
        String leadReviewerDisplayName,
        OffsetDateTime dueAt,
        String consensusStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
