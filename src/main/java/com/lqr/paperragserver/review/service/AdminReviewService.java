package com.lqr.paperragserver.review.service;

import com.lqr.paperragserver.document.dto.PageResponse;
import com.lqr.paperragserver.review.dto.AdminReviewTaskDetailResponse;
import com.lqr.paperragserver.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.paperragserver.review.dto.ReviewAssignmentRequest;
import com.lqr.paperragserver.review.dto.ReviewAssignmentResponse;
import com.lqr.paperragserver.review.dto.ReviewerLoadResponse;
import com.lqr.paperragserver.review.dto.ReviewConsensusResponse;
import com.lqr.paperragserver.review.dto.ReviewConsensusUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface AdminReviewService {

    PageResponse<AdminReviewTaskSummaryResponse> listTasks(String keyword, String status, int page, int size);

    AdminReviewTaskDetailResponse getTask(UUID taskId);

    List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request);

    List<ReviewerLoadResponse> listReviewerLoads();

    ReviewConsensusResponse recalculateConsensus(UUID taskId);

    ReviewConsensusResponse updateConsensus(UUID taskId, ReviewConsensusUpdateRequest request);

    ReviewConsensusResponse confirmConsensus(UUID taskId, UUID operatorUserId);
}
