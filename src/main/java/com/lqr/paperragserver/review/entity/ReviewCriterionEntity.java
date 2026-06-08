package com.lqr.paperragserver.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName(value = "public.review_criterion", autoResultMap = true)
public class ReviewCriterionEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private String code;
    private String name;
    private String description;
    private Integer maxScore;
    private Integer weight;
    private Integer version;
    private String category;
    private Boolean evidenceRequired;

    @TableField(value = "scoring_rules", typeHandler = JsonbTypeHandler.class)
    private Object scoringRules;

    private Boolean enabled;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}