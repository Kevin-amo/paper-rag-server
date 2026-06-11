package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 评审审计日志实体，记录评审流程中的所有操作变更，用于追溯和审计。
 */
@Data
@TableName(value = "public.review_audit_log", autoResultMap = true)
public class ReviewAuditLogEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 日志记录唯一标识 */
    private UUID id;

    /** 关联的评审任务ID */
    private UUID taskId;
    /** 执行操作的用户ID */
    private UUID operatorUserId;
    /** 操作类型（如：assign、submit、approve） */
    private String action;
    /** 操作备注说明 */
    private String note;

    /** 操作时的状态快照（JSON格式） */
    @TableField(value = "snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> snapshot;

    /** 操作前的状态快照（JSON格式） */
    @TableField(value = "before_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> beforeSnapshot;

    /** 操作后的状态快照（JSON格式） */
    @TableField(value = "after_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> afterSnapshot;

    /** 操作前后的差异信息（JSON格式） */
    @TableField(value = "diff", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> diff;

    /** 客户端环境信息（JSON格式，如IP、浏览器等） */
    @TableField(value = "client_info", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> clientInfo;

    /** 记录创建时间 */
    private OffsetDateTime createdAt;
}
