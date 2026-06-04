package com.lqr.paperragserver.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("public.review_task")
public class ReviewTaskEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID documentId;
    private UUID submitterUserId;
    private UUID reviewerUserId;
    private String sourceId;
    private String title;
    private String status;
    private OffsetDateTime assignedAt;
    private OffsetDateTime dueAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}