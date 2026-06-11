package com.lqr.papermind.review.dto;

import com.lqr.papermind.document.dto.DocumentDetailResponse;
import com.lqr.papermind.review.entity.ReviewTaskEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审阅任务的响应DTO，包含任务基本信息、关联文档、审阅报告及分配信息。
 */
public record ReviewTaskResponse(
        /** 任务唯一标识 */
        UUID id,
        /** 关联文档ID */
        UUID documentId,
        /** 提交者用户ID */
        UUID submitterUserId,
        /** 当前审阅者用户ID */
        UUID reviewerUserId,
        /** 论文来源标识 */
        String sourceId,
        /** 论文标题 */
        String title,
        /** 任务状态，如PENDING、IN_PROGRESS、COMPLETED等 */
        String status,
        /** 分配时间 */
        OffsetDateTime assignedAt,
        /** 截止时间 */
        OffsetDateTime dueAt,
        /** 完成时间 */
        OffsetDateTime completedAt,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt,
        /** 关联文档详情 */
        DocumentDetailResponse document,
        /** 审阅报告 */
        ReviewReportResponse report,
        /** 当前分配信息 */
        ReviewAssignmentResponse currentAssignment,
        /** 历史分配记录列表 */
        List<ReviewAssignmentResponse> assignments
) {
    /**
     * 从实体对象构建响应DTO（不含分配信息）。
     *
     * @param entity   审阅任务实体
     * @param document 文档详情响应
     * @param report   审阅报告响应
     * @return 审阅任务响应DTO
     */
    public static ReviewTaskResponse from(ReviewTaskEntity entity, DocumentDetailResponse document, ReviewReportResponse report) {
        return from(entity, document, report, null, List.of());
    }

    /**
     * 从实体对象构建响应DTO（含分配信息）。
     *
     * @param entity             审阅任务实体
     * @param document           文档详情响应
     * @param report             审阅报告响应
     * @param currentAssignment  当前分配信息，可为null
     * @param assignments        分配记录列表，可为null
     * @return 审阅任务响应DTO
     */
    public static ReviewTaskResponse from(
            ReviewTaskEntity entity,
            DocumentDetailResponse document,
            ReviewReportResponse report,
            ReviewAssignmentResponse currentAssignment,
            List<ReviewAssignmentResponse> assignments
    ) {
        return new ReviewTaskResponse(
                entity.getId(),
                entity.getDocumentId(),
                entity.getSubmitterUserId(),
                entity.getReviewerUserId(),
                entity.getSourceId(),
                entity.getTitle(),
                entity.getStatus(),
                entity.getAssignedAt(),
                entity.getDueAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                document,
                report,
                currentAssignment,
                assignments == null ? List.of() : assignments
        );
    }
}
