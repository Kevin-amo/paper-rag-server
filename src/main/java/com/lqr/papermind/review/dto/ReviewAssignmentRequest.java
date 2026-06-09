package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewAssignmentRequest(
        @NotEmpty List<UUID> reviewerUserIds,
        @NotNull UUID leadReviewerUserId,
        OffsetDateTime dueAt
) {
}
