package com.lqr.paperragserver.review.dto;

import com.lqr.paperragserver.review.entity.ReviewRiskItemEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ReviewRiskItemResponse(
        UUID id,
        UUID reportId,
        UUID taskId,
        String riskType,
        String riskLevel,
        String evidence,
        Map<String, Object> evidenceLocation,
        String suggestion,
        String detector,
        BigDecimal confidence,
        String status,
        String reviewerNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewRiskItemResponse from(ReviewRiskItemEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReviewRiskItemResponse(
                entity.getId(),
                entity.getReportId(),
                entity.getTaskId(),
                entity.getRiskType(),
                entity.getRiskLevel(),
                entity.getEvidence(),
                entity.getEvidenceLocation(),
                entity.getSuggestion(),
                entity.getDetector(),
                entity.getConfidence(),
                entity.getStatus(),
                entity.getReviewerNote(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
