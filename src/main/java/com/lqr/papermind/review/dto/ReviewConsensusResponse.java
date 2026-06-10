package com.lqr.papermind.review.dto;

import com.lqr.papermind.review.entity.ReviewConsensusEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewConsensusResponse(
        UUID id,
        UUID taskId,
        UUID leadReviewerUserId,
        String leadReviewerUsername,
        String leadReviewerDisplayName,
        UUID confirmedByUserId,
        String confirmedByUsername,
        String confirmedByDisplayName,
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
        return from(entity, submittedReports, null, null, null, null);
    }

    public static ReviewConsensusResponse from(
            ReviewConsensusEntity entity,
            List<ReviewReportResponse> submittedReports,
            String leadReviewerUsername,
            String leadReviewerDisplayName,
            String confirmedByUsername,
            String confirmedByDisplayName
    ) {
        if (entity == null) {
            return null;
        }
        return new ReviewConsensusResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getLeadReviewerUserId(),
                leadReviewerUsername,
                leadReviewerDisplayName,
                entity.getConfirmedByUserId(),
                confirmedByUsername,
                confirmedByDisplayName,
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
