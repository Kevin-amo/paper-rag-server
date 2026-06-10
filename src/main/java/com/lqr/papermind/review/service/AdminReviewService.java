package com.lqr.papermind.review.service;

import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskDetailResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface AdminReviewService {

    PageResponse<AdminReviewTaskSummaryResponse> listTasks(String keyword, String status, int page, int size);

    AdminReviewTaskDetailResponse getTask(UUID taskId);

    List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request);

    List<ReviewerLoadResponse> listReviewerLoads();

    ReviewConsensusResponse recalculateConsensus(UUID taskId, UUID operatorUserId);

    ReviewConsensusResponse updateConsensus(UUID taskId, UUID operatorUserId, ReviewConsensusUpdateRequest request);

    ReviewConsensusResponse confirmConsensus(UUID taskId, UUID operatorUserId);
}
