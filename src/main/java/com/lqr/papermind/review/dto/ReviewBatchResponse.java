package com.lqr.papermind.review.dto;

import com.lqr.papermind.review.entity.ReviewBatchEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewBatchResponse(
        UUID id,
        String name,
        String description,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewBatchResponse from(ReviewBatchEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReviewBatchResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getCreatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}