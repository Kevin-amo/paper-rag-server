package com.lqr.paperragserver.review.service;

import com.lqr.paperragserver.review.dto.ReviewAssignmentRequest;
import com.lqr.paperragserver.review.dto.ReviewAssignmentResponse;
import com.lqr.paperragserver.review.dto.ReviewerLoadResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewAssignmentService {

    List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request);

    List<ReviewAssignmentResponse> listAssignments(UUID taskId);

    ReviewAssignmentResponse submitAssignment(UUID currentUserId, UUID assignmentId);

    List<ReviewerLoadResponse> listReviewerLoads();
}
