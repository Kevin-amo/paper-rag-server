package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评审小组实体，表示一个评审小组，由多名评审人组成以协同完成评审工作。
 */
@Data
@TableName("public.review_group")
public class ReviewGroupEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 小组唯一标识 */
    private UUID id;

    /** 所属评审批次ID */
    private UUID batchId;
    /** 小组名称 */
    private String name;
    /** 小组负责人用户ID */
    private UUID leaderUserId;
    /** 小组状态（如：active、completed、dissolved） */
    private String status;
    /** 创建小组的用户ID */
    private UUID createdByUserId;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
