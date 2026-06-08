package com.lqr.paperragserver.review.dto;

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
        OffsetDateTime dueAt,
        String consensusStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
