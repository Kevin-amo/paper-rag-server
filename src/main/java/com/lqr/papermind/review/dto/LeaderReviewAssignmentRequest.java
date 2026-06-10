package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LeaderReviewAssignmentRequest(
        @NotEmpty List<UUID> reviewerUserIds,
        OffsetDateTime dueAt
) {
}