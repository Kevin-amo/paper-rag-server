package com.lqr.papermind.review.service;

import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.document.structured.dto.PaperStructuredParseResponse;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewCriterionRequest;
import com.lqr.papermind.review.dto.ReviewCriterionResponse;
import com.lqr.papermind.review.dto.ReviewReportResponse;
import com.lqr.papermind.review.dto.ReviewReportUpdateRequest;
import com.lqr.papermind.review.dto.ReviewRiskItemResponse;
import com.lqr.papermind.review.dto.ReviewRiskUpdateRequest;
import com.lqr.papermind.review.dto.ReviewTaskCreateRequest;
import com.lqr.papermind.review.dto.ReviewTaskResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    PageResponse<ReviewTaskResponse> listTasks(UUID currentUserId, boolean admin, String keyword, String status, int page, int size);

    ReviewTaskResponse getTask(UUID currentUserId, boolean admin, UUID taskId);

    ReviewTaskResponse createTask(UUID currentUserId, ReviewTaskCreateRequest request);

    void createTaskForIndexedReviewDocument(UUID ownerUserId, String sourceId);

    ReviewReportResponse generateAiReview(UUID currentUserId, boolean admin, UUID taskId);

    PaperStructuredParseResponse getStructuredParse(UUID currentUserId, boolean admin, UUID taskId);

    PaperStructuredParseResponse regenerateStructuredParse(UUID currentUserId, boolean admin, UUID taskId);

    ReviewReportResponse updateReport(UUID currentUserId, boolean admin, UUID reportId, ReviewReportUpdateRequest request);

    ReviewAssignmentResponse submitAssignment(UUID currentUserId, UUID assignmentId);

    ReviewConsensusResponse getConsensus(UUID currentUserId, boolean admin, UUID taskId);

    ReviewConsensusResponse updateConsensus(UUID currentUserId, boolean admin, UUID taskId, ReviewConsensusUpdateRequest request);

    ReviewConsensusResponse confirmConsensus(UUID currentUserId, boolean admin, UUID taskId);

    List<ReviewRiskItemResponse> listRisks(UUID currentUserId, boolean admin, UUID reportId);

    ReviewRiskItemResponse updateRisk(UUID currentUserId, boolean admin, UUID riskId, ReviewRiskUpdateRequest request);

    List<ReviewCriterionResponse> listCriteria(boolean includeDisabled);

    ReviewCriterionResponse createCriterion(ReviewCriterionRequest request);

    ReviewCriterionResponse updateCriterion(UUID id, ReviewCriterionRequest request);
}
