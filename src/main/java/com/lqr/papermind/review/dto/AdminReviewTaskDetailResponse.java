package com.lqr.papermind.review.dto;

import java.util.List;

/**
 * 管理员审阅任务详情响应DTO，包含任务完整信息、分配记录、已提交报告及共识结果。
 */
public record AdminReviewTaskDetailResponse(
        /** 审阅任务详情 */
        ReviewTaskResponse task,
        /** 分配记录列表 */
        List<ReviewAssignmentResponse> assignments,
        /** 已提交的审阅报告列表 */
        List<ReviewReportResponse> submittedReports,
        /** 共识结果 */
        ReviewConsensusResponse consensus
) {
}
