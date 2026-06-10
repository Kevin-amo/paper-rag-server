package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName("public.review_group_member")
public class ReviewGroupMemberEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    private UUID groupId;
    private UUID userId;
    private String memberRole;
    private String status;
    private OffsetDateTime joinedAt;
    private OffsetDateTime removedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}