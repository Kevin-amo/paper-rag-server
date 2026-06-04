package com.lqr.paperragserver.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

public record ReviewReportUpdateRequest(
        Map<String, Object> paperSections,
        Object scores,
        Map<String, Object> comments,
        Object risks,
        @Min(0) @Max(100) Integer totalScore,
        String finalRecommendation,
        String status
) {
}