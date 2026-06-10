package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("public.review_assignment")
public class ReviewAssignmentEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID reviewerUserId;
    private UUID groupId;
    private UUID assignedByUserId;
    private String role;
    private String status;
    private OffsetDateTime assignedAt;
    private OffsetDateTime dueAt;
    private OffsetDateTime submittedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
