package com.lqr.papermind.review.dto;

import com.lqr.papermind.review.entity.ReviewCriterionEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审阅指标响应DTO，包含指标的完整信息及默认值。
 */
public record ReviewCriterionResponse(
        /** 指标唯一标识 */
        UUID id,
        /** 指标编码 */
        String code,
        /** 指标名称 */
        String name,
        /** 指标描述说明 */
        String description,
        /** 最高分值，默认100 */
        int maxScore,
        /** 权重，默认20 */
        int weight,
        /** 版本号，默认1 */
        int version,
        /** 指标分类 */
        String category,
        /** 是否要求提供证据，默认true */
        boolean evidenceRequired,
        /** 评分规则 */
        Object scoringRules,
        /** 是否启用，默认true */
        boolean enabled,
        /** 排序序号，默认0 */
        int sortOrder,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
    /**
     * 从指标实体构建响应DTO。
     *
     * @param entity 指标实体
     * @return 指标响应DTO
     */
    public static ReviewCriterionResponse from(ReviewCriterionEntity entity) {
        return new ReviewCriterionResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getMaxScore() == null ? 100 : entity.getMaxScore(),
                entity.getWeight() == null ? 20 : entity.getWeight(),
                entity.getVersion() == null ? 1 : entity.getVersion(),
                entity.getCategory(),
                entity.getEvidenceRequired() == null || entity.getEvidenceRequired(),
                entity.getScoringRules(),
                entity.getEnabled() == null || entity.getEnabled(),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}