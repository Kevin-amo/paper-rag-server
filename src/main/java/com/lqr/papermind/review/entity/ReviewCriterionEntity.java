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
 * 评审标准实体，定义论文评审所使用的评分标准和规则。
 */
@Data
@TableName(value = "public.review_criterion", autoResultMap = true)
public class ReviewCriterionEntity {

    @TableId(value = "id", type = IdType.INPUT)
    /** 标准唯一标识 */
    private UUID id;

    /** 标准编码，用于程序化引用 */
    private String code;
    /** 标准名称 */
    private String name;
    /** 标准描述说明 */
    private String description;
    /** 该项满分值 */
    private Integer maxScore;
    /** 权重系数 */
    private Integer weight;
    /** 标准版本号 */
    private Integer version;
    /** 所属分类（如：创新性、规范性、可复现性） */
    private String category;
    /** 是否要求提供评审依据 */
    private Boolean evidenceRequired;

    /** 评分规则定义（JSON格式） */
    @TableField(value = "scoring_rules", typeHandler = JsonbTypeHandler.class)
    private Object scoringRules;

    /** 是否启用该标准 */
    private Boolean enabled;
    /** 排序序号 */
    private Integer sortOrder;
    /** 记录创建时间 */
    private OffsetDateTime createdAt;
    /** 记录更新时间 */
    private OffsetDateTime updatedAt;
}
