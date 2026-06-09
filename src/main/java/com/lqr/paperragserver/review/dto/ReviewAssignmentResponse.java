package com.lqr.paperragserver.review.dto;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.review.entity.ReviewAssignmentEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewAssignmentResponse(
        UUID id,
        UUID taskId,
        UUID reviewerUserId,
        String reviewerUsername,
        String reviewerDisplayName,
        String role,
        String status,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewAssignmentResponse from(ReviewAssignmentEntity entity) {
        return from(entity, null);
    }

    public static ReviewAssignmentResponse from(ReviewAssignmentEntity entity, SysUser reviewer) {
        if (entity == null) {
            return null;
        }
        return new ReviewAssignmentResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getReviewerUserId(),
                reviewer == null ? null : reviewer.getUsername(),
                reviewer == null ? null : reviewer.getDisplayName(),
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
