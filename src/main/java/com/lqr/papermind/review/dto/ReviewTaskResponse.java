package com.lqr.papermind.review.dto;

import com.lqr.papermind.document.dto.DocumentDetailResponse;
import com.lqr.papermind.review.entity.ReviewTaskEntity;

import java.time.OffsetDateTime;
import java.util.List;
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
        ReviewReportResponse report,
        ReviewAssignmentResponse currentAssignment,
        List<ReviewAssignmentResponse> assignments
) {
    public static ReviewTaskResponse from(ReviewTaskEntity entity, DocumentDetailResponse document, ReviewReportResponse report) {
        return from(entity, document, report, null, List.of());
    }

    public static ReviewTaskResponse from(
            ReviewTaskEntity entity,
            DocumentDetailResponse document,
            ReviewReportResponse report,
            ReviewAssignmentResponse currentAssignment,
            List<ReviewAssignmentResponse> assignments
    ) {
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
                report,
                currentAssignment,
                assignments == null ? List.of() : assignments
        );
    }
}
