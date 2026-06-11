package com.lqr.papermind.review.dto;

import com.lqr.papermind.review.entity.ReviewRiskItemEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 审阅风险项响应DTO，包含风险类型、等级、证据及处理状态等完整信息。
 */
public record ReviewRiskItemResponse(
        /** 风险项唯一标识 */
        UUID id,
        /** 关联报告ID */
        UUID reportId,
        /** 关联任务ID */
        UUID taskId,
        /** 风险类型，如抄袭、数据造假等 */
        String riskType,
        /** 风险等级，如HIGH、MEDIUM、LOW */
        String riskLevel,
        /** 风险证据描述 */
        String evidence,
        /** 证据位置信息，包含章节、页码等 */
        Map<String, Object> evidenceLocation,
        /** 处理建议 */
        String suggestion,
        /** 检测器标识，如AI检测、人工标注等 */
        String detector,
        /** 置信度 */
        BigDecimal confidence,
        /** 风险状态，如OPEN、CONFIRMED、IGNORED、RESOLVED */
        String status,
        /** 审阅者备注 */
        String reviewerNote,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
    /**
     * 从风险项实体构建响应DTO。
     *
     * @param entity 风险项实体
     * @return 风险项响应DTO，实体为null时返回null
     */
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
