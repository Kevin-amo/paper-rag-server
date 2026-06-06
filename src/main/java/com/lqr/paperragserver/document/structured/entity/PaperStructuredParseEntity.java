package com.lqr.paperragserver.document.structured.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 论文结构化解析持久化实体。
 */
@Data
@TableName(value = "public.paper_structured_parse", autoResultMap = true)
public class PaperStructuredParseEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID ownerUserId;
    private UUID documentId;
    private String sourceId;
    private String rawText;

    @TableField(value = "rule_result", typeHandler = JsonbTypeHandler.class)
    private Object ruleResult;

    @TableField(value = "model_result", typeHandler = JsonbTypeHandler.class)
    private Object modelResult;

    @TableField(value = "merged_result", typeHandler = JsonbTypeHandler.class)
    private Object mergedResult;

    @TableField(value = "field_confidence", typeHandler = JsonbTypeHandler.class)
    private Object fieldConfidence;

    @TableField(value = "missing_fields", typeHandler = JsonbTypeHandler.class)
    private Object missingFields;

    @TableField(value = "low_confidence_fields", typeHandler = JsonbTypeHandler.class)
    private Object lowConfidenceFields;

    private String rawModelOutput;
    private String status;
    private String errorMessage;
    private OffsetDateTime parsedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}