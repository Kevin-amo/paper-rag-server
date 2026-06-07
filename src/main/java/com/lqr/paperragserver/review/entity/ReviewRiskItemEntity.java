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
@TableName(value = "public.review_risk_item", autoResultMap = true)
public class ReviewRiskItemEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID reportId;
    private UUID taskId;
    private String riskType;
    private String riskLevel;
    private String evidence;

    @TableField(value = "evidence_location", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> evidenceLocation;

    private String suggestion;
    private String detector;
    private BigDecimal confidence;
    private String status;
    private String reviewerNote;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
