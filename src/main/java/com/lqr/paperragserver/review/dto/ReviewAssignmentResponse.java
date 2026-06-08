package com.lqr.paperragserver.review.dto;

import com.lqr.paperragserver.review.entity.ReviewAssignmentEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewAssignmentResponse(
        UUID id,
        UUID taskId,
        UUID reviewerUserId,
        String role,
        String status,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewAssignmentResponse from(ReviewAssignmentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReviewAssignmentResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getReviewerUserId(),
                entity.getRole(),
                entity.getStatus(),
                entity.getAssignedAt(),
                entity.getDueAt(),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
