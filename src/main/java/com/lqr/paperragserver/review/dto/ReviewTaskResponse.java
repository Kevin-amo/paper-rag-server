package com.lqr.paperragserver.review.dto;

import com.lqr.paperragserver.document.dto.DocumentDetailResponse;
import com.lqr.paperragserver.review.entity.ReviewTaskEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewTaskResponse(
        UUID id,
        UUID documentId,
        UUID submitterUserId,
        UUID reviewerUserId,
        String sourceId,
        String title,
        String status,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        DocumentDetailResponse document,
        ReviewReportResponse report
) {
    public static ReviewTaskResponse from(ReviewTaskEntity entity, DocumentDetailResponse document, ReviewReportResponse report) {
        return new ReviewTaskResponse(
                entity.getId(),
                entity.getDocumentId(),
                entity.getSubmitterUserId(),
                entity.getReviewerUserId(),
                entity.getSourceId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getAssignedAt(),
                entity.getDueAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                document,
                report
        );
    }
}