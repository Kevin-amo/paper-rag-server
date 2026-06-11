package com.lqr.papermind.review.dto;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 审阅任务分配的响应DTO，包含分配详情及审阅者信息。
 */
public record ReviewAssignmentResponse(
        /** 分配记录唯一标识 */
        UUID id,
        /** 关联任务ID */
        UUID taskId,
        /** 审阅者用户ID */
        UUID reviewerUserId,
        /** 审阅者用户名 */
        String reviewerUsername,
        /** 审阅者显示名称 */
        String reviewerDisplayName,
        /** 审阅角色，如LEAD、REVIEWER */
        String role,
        /** 分配状态，如ASSIGNED、SUBMITTED等 */
        String status,
        /** 分配时间 */
        OffsetDateTime assignedAt,
        /** 截止时间 */
        OffsetDateTime dueAt,
        /** 提交时间 */
        OffsetDateTime submittedAt,
        /** 创建时间 */
        OffsetDateTime createdAt,
        /** 更新时间 */
        OffsetDateTime updatedAt
) {
    /**
     * 从分配实体构建响应DTO（不含审阅者信息）。
     *
     * @param entity 分配实体
     * @return 分配响应DTO，实体为null时返回null
     */
    public static ReviewAssignmentResponse from(ReviewAssignmentEntity entity) {
        return from(entity, null);
    }

    /**
     * 从分配实体和审阅者信息构建响应DTO。
     *
     * @param entity   分配实体
     * @param reviewer 审阅者用户信息，可为null
     * @return 分配响应DTO，实体为null时返回null
     */
    public static ReviewAssignmentResponse from(ReviewAssignmentEntity entity, SysUser reviewer) {
        if (entity == null) {
            return null;
        }
        return new ReviewAssignmentResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getReviewerUserId(),
                reviewer == null ? null : reviewer.getUsername(),
                reviewer == null ? null : reviewer.getDisplayName(),
                entity.getRole(),
                entity.getStatus(),
                entity.getAssignedAt(),
                entity.getDueAt(),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
