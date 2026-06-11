package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 审阅风险项更新请求DTO，用于更新风险项的状态和审阅者备注。
 */
public record ReviewRiskUpdateRequest(
        /** 风险状态，可选值：OPEN、CONFIRMED、IGNORED、RESOLVED */
        @Pattern(regexp = "OPEN|CONFIRMED|IGNORED|RESOLVED") String status,
        /** 审阅者备注说明 */
        String reviewerNote
) {
}
