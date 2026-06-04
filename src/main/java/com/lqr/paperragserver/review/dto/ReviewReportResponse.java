package com.lqr.paperragserver.review.dto;

import com.lqr.paperragserver.review.entity.ReviewReportEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ReviewReportResponse(
        UUID id,
        UUID taskId,
        UUID documentId,
        UUID reviewerUserId,
        Map<String, Object> paperSections,
        Object scores,
        Map<String, Object> comments,
        Object risks,
        Integer totalScore,
        String finalRecommendation,
        String status,
        OffsetDateTime generatedAt,
        OffsetDateTime adjustedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewReportResponse from(ReviewReportEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReviewReportResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getDocumentId(),
                entity.getReviewerUserId(),
                entity.getPaperSections(),
                entity.getScores(),
                entity.getComments(),
                entity.getRisks(),
                entity.getTotalScore(),
                entity.getFinalRecommendation(),
                entity.getStatus(),
                entity.getGeneratedAt(),
                entity.getAdjustedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}