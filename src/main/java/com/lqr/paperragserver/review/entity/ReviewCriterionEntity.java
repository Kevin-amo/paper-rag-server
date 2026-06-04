package com.lqr.paperragserver.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("public.review_criterion")
public class ReviewCriterionEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String code;
    private String name;
    private String description;
    private Integer maxScore;
    private Integer weight;
    private Boolean enabled;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}