package com.lqr.paperragserver.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@TableName(value = "public.review_audit_log", autoResultMap = true)
public class ReviewAuditLogEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID taskId;
    private UUID operatorUserId;
    private String action;
    private String note;

    @TableField(value = "snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> snapshot;

    @TableField(value = "before_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> beforeSnapshot;

    @TableField(value = "after_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> afterSnapshot;

    @TableField(value = "diff", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> diff;

    @TableField(value = "client_info", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> clientInfo;

    private OffsetDateTime createdAt;
}