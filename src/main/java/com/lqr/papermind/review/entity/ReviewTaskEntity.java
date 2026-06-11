package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评审任务实体，表示分配给评审人的单篇论文评审任务。
 */
@Data
@TableName("public.review_task")
public class ReviewTaskEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 任务唯一标识 */
    private UUID id;

    /** 关联的文档ID */
    private UUID documentId;
    /** 提交论文的用户ID */
    private UUID submitterUserId;
    /** 被分配的评审人用户ID */
    private UUID reviewerUserId;
    /** 所属评审批次ID */
    private UUID batchId;
    /** 所属评审小组ID */
    private UUID groupId;
    /** 分配该任务的用户ID */
    private UUID assignedByUserId;
    /** 评审小组负责人用户ID */
    private UUID leaderUserId;
    /** 外部来源标识，用于追溯任务来源 */
    private String sourceId;
    /** 评审任务标题 */
    private String title;
    /** 任务状态（如：pending、in_progress、completed） */
    private String status;
    /** 任务分配时间 */
    private OffsetDateTime assignedAt;
    /** 任务截止时间 */
    private OffsetDateTime dueAt;
    /** 任务完成时间 */
    private OffsetDateTime completedAt;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
