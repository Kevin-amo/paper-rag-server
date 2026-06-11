package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 审阅指标创建/更新请求DTO，定义评审指标的编码、名称、权重及评分规则。
 */
public record ReviewCriterionRequest(
        /** 指标编码，不能为空 */
        @NotBlank(message = "指标编码不能为空") String code,
        /** 指标名称，不能为空 */
        @NotBlank(message = "指标名称不能为空") String name,
        /** 指标描述说明 */
        String description,
        /** 该指标的最高分值，范围1-100 */
        @Min(1) @Max(100) Integer maxScore,
        /** 该指标的权重，范围1-100 */
        @Min(1) @Max(100) Integer weight,
        /** 指标版本号，从1开始 */
        @Min(1) Integer version,
        /** 指标分类 */
        String category,
        /** 是否要求提供证据 */
        Boolean evidenceRequired,
        /** 评分规则列表，包含各分数段的评判标准 */
        List<Map<String, Object>> scoringRules,
        /** 是否启用该指标 */
        Boolean enabled,
        /** 排序序号 */
        Integer sortOrder
) {
}