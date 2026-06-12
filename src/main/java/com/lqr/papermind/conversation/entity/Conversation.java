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

    /** 会话主键，客户端指定 UUID */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /** 会话所属用户 ID */
    private UUID ownerUserId;
    /** 会话标题 */
    private String title;
    /** 创建时间 */
    private OffsetDateTime createdAt;
    /** 最后更新时间 */
    private OffsetDateTime updatedAt;
    /** 软删除时间，非空表示已删除 */
    private OffsetDateTime deletedAt;
}