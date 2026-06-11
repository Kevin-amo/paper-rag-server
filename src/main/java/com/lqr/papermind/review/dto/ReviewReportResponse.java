package com.lqr.papermind.review.dto;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.review.entity.ReviewReportEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 审阅报告的响应DTO，包含完整的审阅报告信息，含评分、意见、风险项等。
 */
public record ReviewReportResponse(
        /** 报告唯一标识 */
        UUID id,
        /** 关联任务ID */
        UUID taskId,
        /** 关联文档ID */
        UUID documentId,
        /** 关联分配记录ID */
        UUID assignmentId,
        /** 审阅者用户ID */
        UUID reviewerUserId,
        /** 审阅者用户名 */
        String reviewerUsername,
        /** 审阅者显示名称 */
        String reviewerDisplayName,
        /** 论文各章节审阅内容 */
        Map<String, Object> paperSections,
        /** 评分数据 */
        Object scores,
        /** 审阅意见 */
        Map<String, Object> comments,
        /** 风险项数据 */
        Object risks,
        /** 评审指标版本号 */
        Integer criterionVersion,
        /** AI模型版本标识 */
        String modelVersion,
        /** 提示词版本标识 */
        String promptVersion,
        /** AI生成置信度 */
        BigDecimal confidence,
        /** 人工调整增量 */
        Map<String, Object> manualDelta,
        /** 总分 */
        Integer totalScore,
        /** 最终推荐意见 */
        String finalRecommendation,
        /** 报告状态 */
        String status,
        /** AI生成时间 */
        OffsetDateTime generatedAt,
        /** 人工调整时间 */
        OffsetDateTime adjustedAt,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
    /**
     * 从报告实体构建响应DTO（不含审阅者信息）。
     *
     * @param entity 报告实体
     * @return 报告响应DTO，实体为null时返回null
     */
    public static ReviewReportResponse from(ReviewReportEntity entity) {
        return from(entity, null);
    }

    /**
     * 从报告实体和审阅者信息构建响应DTO。
     *
     * @param entity   报告实体
     * @param reviewer 审阅者用户信息，可为null
     * @return 报告响应DTO，实体为null时返回null
     */
    public static ReviewReportResponse from(ReviewReportEntity entity, SysUser reviewer) {
        if (entity == null) {
            return null;
        }
        return new ReviewReportResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getDocumentId(),
                entity.getAssignmentId(),
                entity.getReviewerUserId(),
                reviewer == null ? null : reviewer.getUsername(),
                reviewer == null ? null : reviewer.getDisplayName(),
                entity.getPaperSections(),
                entity.getScores(),
                entity.getComments(),
                entity.getRisks(),
                entity.getCriterionVersion(),
                entity.getModelVersion(),
                entity.getPromptVersion(),
                entity.getConfidence(),
                entity.getManualDelta(),
                entity.getTotalScore(),
                entity.getFinalRecommendation(),
                entity.getStatus(),
                entity.getGeneratedAt(),
                entity.getAdjustedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
