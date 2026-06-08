package com.lqr.paperragserver.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record ReviewCriterionRequest(
        @NotBlank(message = "指标编码不能为空") String code,
        @NotBlank(message = "指标名称不能为空") String name,
        String description,
        @Min(1) @Max(100) Integer maxScore,
        @Min(1) @Max(100) Integer weight,
        @Min(1) Integer version,
        String category,
        Boolean evidenceRequired,
        List<Map<String, Object>> scoringRules,
        Boolean enabled,
        Integer sortOrder
) {
}