package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评审共识实体，记录多位评审人对同一任务达成的最终共识结果。
 */
@Data
@TableName(value = "public.review_consensus", autoResultMap = true)
public class ReviewConsensusEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 共识记录唯一标识 */
    private UUID id;

    /** 关联的评审任务ID */
    private UUID taskId;
    /** 负责协调共识的主评审人用户ID */
    private UUID leadReviewerUserId;
    /** 确认共识结果的用户ID */
    private UUID confirmedByUserId;

    /** 各评审人评分汇总（JSON格式） */
    @TableField(value = "score_summary", typeHandler = JsonbTypeHandler.class)
    private Object scoreSummary;

    /** 各评审人评语汇总（JSON格式） */
    @TableField(value = "comment_summary", typeHandler = JsonbTypeHandler.class)
    private Object commentSummary;

    /** 存在分歧的评审项（JSON格式） */
    @TableField(value = "disagreement_items", typeHandler = JsonbTypeHandler.class)
    private Object disagreementItems;

    /** 最终共识总分 */
    private Integer finalScore;
    /** 最终共识建议（如：accept、reject、revise） */
    private String finalRecommendation;
    /** 共识状态（如：pending、resolved、confirmed） */
    private String status;
    /** 共识确认时间 */
    private OffsetDateTime confirmedAt;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
