package com.lqr.papermind.review.dto;

import com.lqr.papermind.review.entity.ReviewConsensusEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewConsensusResponse(
        /** 共识记录唯一标识 */
        UUID id,
        /** 关联任务ID */
        UUID taskId,
        /** 主审阅者（组长）用户ID */
        UUID leadReviewerUserId,
        /** 主审阅者用户名 */
        String leadReviewerUsername,
        /** 主审阅者显示名称 */
        String leadReviewerDisplayName,
        /** 确认人用户ID */
        UUID confirmedByUserId,
        /** 确认人用户名 */
        String confirmedByUsername,
        /** 确认人显示名称 */
        String confirmedByDisplayName,
        /** 各审阅者评分汇总 */
        Object scoreSummary,
        /** 各审阅者意见汇总 */
        Object commentSummary,
        /** 审阅意见分歧项列表 */
        Object disagreementItems,
        /** 最终综合评分 */
        Integer finalScore,
        /** 最终推荐意见 */
        String finalRecommendation,
        /** 共识状态，如PENDING、CONFIRMED等 */
        String status,
        /** 确认时间 */
        OffsetDateTime confirmedAt,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt,
        /** 已提交的审阅报告列表 */
        List<ReviewReportResponse> submittedReports
) {
    /**
     * 从共识实体构建响应DTO（不含用户名称信息）。
     *
     * @param entity          共识实体
     * @param submittedReports 已提交的审阅报告列表
     * @return 共识响应DTO
     */
    public static ReviewConsensusResponse from(ReviewConsensusEntity entity, List<ReviewReportResponse> submittedReports) {
        return from(entity, submittedReports, null, null, null, null);
    }

    /**
     * 从共识实体构建响应DTO（含用户名称信息）。
     *
     * @param entity                    共识实体
     * @param submittedReports          已提交的审阅报告列表
     * @param leadReviewerUsername      主审阅者用户名
     * @param leadReviewerDisplayName   主审阅者显示名称
     * @param confirmedByUsername       确认人用户名
     * @param confirmedByDisplayName    确认人显示名称
     * @return 共识响应DTO，实体为null时返回null
     */
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
