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
 * 评审风险项实体，记录评审过程中发现的论文风险或问题。
 */
@Data
@TableName(value = "public.review_risk_item", autoResultMap = true)
public class ReviewRiskItemEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 风险项唯一标识 */
    private UUID id;

    /** 关联的评审报告ID */
    private UUID reportId;
    /** 关联的评审任务ID */
    private UUID taskId;
    /** 风险类型（如：plagiarism、methodology、data_integrity） */
    private String riskType;
    /** 风险等级（如：high、medium、low） */
    private String riskLevel;
    /** 风险证据描述 */
    private String evidence;

    /** 证据在论文中的定位信息（JSON格式，如章节、页码等） */
    @TableField(value = "evidence_location", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> evidenceLocation;

    /** 风险处理建议 */
    private String suggestion;
    /** 检测来源（如：ai_detector、reviewer、plagiarism_tool） */
    private String detector;
    /** 检测置信度 */
    private BigDecimal confidence;
    /** 风险状态（如：open、acknowledged、resolved） */
    private String status;
    /** 评审人的处理备注 */
    private String reviewerNote;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
