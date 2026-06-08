package com.lqr.paperragserver.review.dto;

import com.lqr.paperragserver.review.entity.ReviewConsensusEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewConsensusResponse(
        UUID id,
        UUID taskId,
        UUID leadReviewerUserId,
        UUID confirmedByUserId,
        Object scoreSummary,
        Object commentSummary,
        Object disagreementItems,
        Integer finalScore,
        String finalRecommendation,
        String status,
        OffsetDateTime confirmedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<ReviewReportResponse> submittedReports
) {
    public static ReviewConsensusResponse from(ReviewConsensusEntity entity, List<ReviewReportResponse> submittedReports) {
        if (entity == null) {
            return null;
        }
        return new ReviewConsensusResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getLeadReviewerUserId(),
                entity.getConfirmedByUserId(),
                entity.getScoreSummary(),
                entity.getCommentSummary(),
                entity.getDisagreementItems(),
                entity.getFinalScore(),
                entity.getFinalRecommendation(),
                entity.getStatus(),
                entity.getConfirmedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                submittedReports == null ? List.of() : submittedReports
        );
    }
}
