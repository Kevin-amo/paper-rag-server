package com.lqr.papermind.review.dto;

import java.util.List;

public record AdminReviewTaskDetailResponse(
        ReviewTaskResponse task,
        List<ReviewAssignmentResponse> assignments,
        List<ReviewReportResponse> submittedReports,
        ReviewConsensusResponse consensus
) {
}
