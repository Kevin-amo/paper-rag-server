package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("public.review_batch")
public class ReviewBatchEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String name;
    private String description;
    private String status;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private UUID createdByUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}