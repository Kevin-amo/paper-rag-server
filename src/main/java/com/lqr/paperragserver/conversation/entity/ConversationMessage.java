package com.lqr.paperragserver.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.paperragserver.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    private OffsetDateTime createdAt;
}