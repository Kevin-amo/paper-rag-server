package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评审批次实体，表示一次评审活动的批次，包含评审时间范围和参与人员等信息。
 */
@Data
@TableName("public.review_batch")
public class ReviewBatchEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 批次唯一标识 */
    private UUID id;

    /** 批次名称 */
    private String name;
    /** 批次描述说明 */
    private String description;
    /** 批次状态（如：pending、active、completed） */
    private String status;
    /** 批次开始时间 */
    private OffsetDateTime startsAt;
    /** 批次结束时间 */
    private OffsetDateTime endsAt;
    /** 创建该批次的用户ID */
    private UUID createdByUserId;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
