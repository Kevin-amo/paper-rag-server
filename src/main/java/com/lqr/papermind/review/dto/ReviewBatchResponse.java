package com.lqr.papermind.review.dto;

import com.lqr.papermind.review.entity.ReviewBatchEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审阅批次响应DTO，包含批次基本信息及时间范围。
 */
public record ReviewBatchResponse(
        /** 批次唯一标识 */
        UUID id,
        /** 批次名称 */
        String name,
        /** 批次描述 */
        String description,
        /** 批次状态 */
        String status,
        /** 开始时间 */
        OffsetDateTime startsAt,
        /** 结束时间 */
        OffsetDateTime endsAt,
        /** 创建者用户ID */
        UUID createdByUserId,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
    /**
     * 从批次实体构建响应DTO。
     *
     * @param entity 批次实体
     * @return 批次响应DTO，实体为null时返回null
     */
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