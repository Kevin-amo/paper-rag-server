package com.lqr.paperragserver.review.dto;

import com.lqr.paperragserver.review.entity.ReviewCriterionEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewCriterionResponse(
        UUID id,
        String code,
        String name,
        String description,
        int maxScore,
        int weight,
        boolean enabled,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewCriterionResponse from(ReviewCriterionEntity entity) {
        return new ReviewCriterionResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getMaxScore() == null ? 100 : entity.getMaxScore(),
                entity.getWeight() == null ? 20 : entity.getWeight(),
                entity.getEnabled() == null || entity.getEnabled(),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}