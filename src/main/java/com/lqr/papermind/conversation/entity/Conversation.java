package com.lqr.papermind.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 会话实体，对应 conversation 表，记录用户会话的标题、归属和软删除状态。
 */
@Data
@TableName("public.conversation")
public class Conversation {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID ownerUserId;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}