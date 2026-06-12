package com.lqr.papermind.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 会话消息实体，对应 conversation_message 表，记录消息内容、角色、顺序、引用和扩展元数据。
 */
@Data
@TableName(value = "public.conversation_message", autoResultMap = true)
public class ConversationMessage {

    /** 消息主键，客户端指定 UUID */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /** 所属会话 ID */
    private UUID conversationId;
    /** 消息所属用户 ID */
    private UUID ownerUserId;
    /** 消息角色，如 USER、ASSISTANT */
    private String role;
    /** 消息在会话中的顺序号 */
    private Integer messageOrder;
    /** 消息文本内容 */
    private String content;

    /** 回答引用列表，以 JSON 存储 */
    @TableField(value = "citations", typeHandler = JsonbTypeHandler.class)
    private Object citations;

    /** 扩展元数据，以 JSON 存储 */
    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private Object metadata;

    /** 消息创建时间 */
    private OffsetDateTime createdAt;
}