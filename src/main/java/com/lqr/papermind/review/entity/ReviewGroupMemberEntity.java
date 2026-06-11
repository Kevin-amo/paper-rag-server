package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评审小组成员实体，记录评审小组中成员的加入和角色信息。
 */
@Data
@TableName("public.review_group_member")
public class ReviewGroupMemberEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 成员记录唯一标识 */
    private UUID id;

    /** 所属评审小组ID */
    private UUID groupId;
    /** 成员用户ID */
    private UUID userId;
    /** 成员角色（如：reviewer、leader、observer） */
    private String memberRole;
    /** 成员状态（如：active、removed） */
    private String status;
    /** 加入小组的时间 */
    private OffsetDateTime joinedAt;
    /** 从小组移除的时间 */
    private OffsetDateTime removedAt;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
