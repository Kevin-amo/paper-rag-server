package com.lqr.paperragserver.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "public.review_report", autoResultMap = true)
public class ReviewReportEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID documentId;
    private UUID reviewerUserId;

    @TableField(value = "paper_sections", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> paperSections;

    @TableField(value = "scores", typeHandler = JsonbTypeHandler.class)
    private Object scores;

    @TableField(value = "comments", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> comments;

    @TableField(value = "risks", typeHandler = JsonbTypeHandler.class)
    private Object risks;

    @TableField(value = "raw_model_output", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawModelOutput;

    private Integer criterionVersion;
    private String modelVersion;
    private String promptVersion;
    private BigDecimal confidence;

    @TableField(value = "manual_delta", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> manualDelta;

    private Integer totalScore;
    private String finalRecommendation;
    private String status;
    private OffsetDateTime generatedAt;
    private OffsetDateTime adjustedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}