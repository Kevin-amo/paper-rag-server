package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评审分配实体，记录评审任务分配给评审人的详细信息。
 */
@Data
@TableName("public.review_assignment")
public class ReviewAssignmentEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 分配记录唯一标识 */
    private UUID id;

    /** 关联的评审任务ID */
    private UUID taskId;
    /** 被分配的评审人用户ID */
    private UUID reviewerUserId;
    /** 评审小组ID */
    private UUID groupId;
    /** 执行分配操作的用户ID */
    private UUID assignedByUserId;
    /** 评审角色（如：reviewer、leader） */
    private String role;
    /** 分配状态（如：assigned、accepted、submitted） */
    private String status;
    /** 分配时间 */
    private OffsetDateTime assignedAt;
    /** 截止时间 */
    private OffsetDateTime dueAt;
    /** 评审提交时间 */
    private OffsetDateTime submittedAt;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
