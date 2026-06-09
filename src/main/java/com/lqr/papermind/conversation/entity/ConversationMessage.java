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

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID conversationId;
    private UUID ownerUserId;
    private String role;
    private Integer messageOrder;
    private String content;

    @TableField(value = "citations", typeHandler = JsonbTypeHandler.class)
    private Object citations;

    @TableField(value = "metadata", typeHandler = JsonbTypeHandler.class)
    private Object metadata;

    private OffsetDateTime createdAt;
}