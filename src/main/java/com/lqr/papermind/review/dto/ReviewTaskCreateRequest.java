package com.lqr.papermind.review.dto;

/**
 * 创建审阅任务的请求DTO，包含论文来源标识和标题信息。
 */
public record ReviewTaskCreateRequest(
        /** 论文来源标识，用于关联外部系统 */
        String sourceId,
        /** 论文标题 */
        String title
) {
}