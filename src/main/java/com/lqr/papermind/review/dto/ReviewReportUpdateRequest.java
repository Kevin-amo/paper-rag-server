package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Map;

/**
 * 审阅报告更新请求DTO，用于更新审阅报告的各项内容。
 */
public record ReviewReportUpdateRequest(
        /** 论文各章节审阅内容，键为章节名 */
        Map<String, Object> paperSections,
        /** 评分数据 */
        Object scores,
        /** 审阅意见，键为评审维度 */
        Map<String, Object> comments,
        /** 风险项数据 */
        Object risks,
        /** 总分，范围0-100 */
        @Min(0) @Max(100) Integer totalScore,
        /** 最终推荐意见，如ACCEPT、REJECT等 */
        String finalRecommendation,
        /** 报告状态，如DRAFT、SUBMITTED等 */
        String status
) {
}