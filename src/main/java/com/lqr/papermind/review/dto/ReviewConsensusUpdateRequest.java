package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 审阅共识更新请求DTO，用于组长确认最终审阅结果。
 */
public record ReviewConsensusUpdateRequest(
        /** 最终综合评分，范围0-100 */
        @Min(0) @Max(100) Integer finalScore,
        /** 最终推荐意见，如ACCEPT、REJECT等 */
        String finalRecommendation
) {
}
