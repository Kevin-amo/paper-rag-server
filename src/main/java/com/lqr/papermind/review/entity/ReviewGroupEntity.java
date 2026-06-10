package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("public.review_group")
public class ReviewGroupEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID batchId;
    private String name;
    private UUID leaderUserId;
    private String status;
    private UUID createdByUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}