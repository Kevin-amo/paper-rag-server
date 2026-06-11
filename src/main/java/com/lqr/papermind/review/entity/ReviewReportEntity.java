package com.lqr.papermind.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 评审报告实体，存储评审人对论文的评审报告，包含评分、评论及风险等信息。
 */
@Data
@TableName(value = "public.review_report", autoResultMap = true)
public class ReviewReportEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 报告唯一标识 */
    private UUID id;

    /** 关联的评审任务ID */
    private UUID taskId;
    /** 关联的文档ID */
    private UUID documentId;
    /** 关联的分配记录ID */
    private UUID assignmentId;
    /** 评审人用户ID */
    private UUID reviewerUserId;

    /** 论文各章节评审内容（JSON格式） */
    @TableField(value = "paper_sections", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> paperSections;

    /** 各评审维度的评分（JSON格式） */
    @TableField(value = "scores", typeHandler = JsonbTypeHandler.class)
    private Object scores;

    /** 各评审维度的评语（JSON格式） */
    @TableField(value = "comments", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> comments;

    /** 发现的风险项列表（JSON格式） */
    @TableField(value = "risks", typeHandler = JsonbTypeHandler.class)
    private Object risks;

    /** 模型原始输出结果（JSON格式） */
    @TableField(value = "raw_model_output", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> rawModelOutput;

    /** 评审标准版本号 */
    private Integer criterionVersion;
    /** 使用的AI模型版本 */
    private String modelVersion;
    /** 使用的提示词版本 */
    private String promptVersion;
    /** 模型输出的置信度 */
    private BigDecimal confidence;

    /** 人工调整的差异记录（JSON格式） */
    @TableField(value = "manual_delta", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> manualDelta;

    /** 最终总分 */
    private Integer totalScore;
    /** 最终评审建议（如：accept、reject、revise） */
    private String finalRecommendation;
    /** 报告状态（如：draft、submitted、finalized） */
    private String status;
    /** 报告生成时间 */
    private OffsetDateTime generatedAt;
    /** 人工调整时间 */
    private OffsetDateTime adjustedAt;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
