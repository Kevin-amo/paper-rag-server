package com.lqr.paperragserver.review.service;

import com.lqr.paperragserver.document.dto.PageResponse;
import com.lqr.paperragserver.review.dto.ReviewCriterionRequest;
import com.lqr.paperragserver.review.dto.ReviewCriterionResponse;
import com.lqr.paperragserver.review.dto.ReviewReportResponse;
import com.lqr.paperragserver.review.dto.ReviewReportUpdateRequest;
import com.lqr.paperragserver.review.dto.ReviewRiskItemResponse;
import com.lqr.paperragserver.review.dto.ReviewRiskUpdateRequest;
import com.lqr.paperragserver.review.dto.ReviewTaskCreateRequest;
import com.lqr.paperragserver.review.dto.ReviewTaskResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    PageResponse<ReviewTaskResponse> listTasks(UUID currentUserId, boolean admin, String keyword, String status, int page, int size);

    ReviewTaskResponse getTask(UUID currentUserId, boolean admin, UUID taskId);

    ReviewTaskResponse createTask(UUID currentUserId, ReviewTaskCreateRequest request);

    void createTaskForIndexedReviewDocument(UUID ownerUserId, String sourceId);

    ReviewReportResponse generateAiReview(UUID currentUserId, boolean admin, UUID taskId);

    ReviewReportResponse updateReport(UUID currentUserId, boolean admin, UUID reportId, ReviewReportUpdateRequest request);

    List<ReviewRiskItemResponse> listRisks(UUID currentUserId, boolean admin, UUID reportId);

    ReviewRiskItemResponse updateRisk(UUID currentUserId, boolean admin, UUID riskId, ReviewRiskUpdateRequest request);

    List<ReviewCriterionResponse> listCriteria(boolean includeDisabled);

    ReviewCriterionResponse createCriterion(ReviewCriterionRequest request);

    ReviewCriterionResponse updateCriterion(UUID id, ReviewCriterionRequest request);
}