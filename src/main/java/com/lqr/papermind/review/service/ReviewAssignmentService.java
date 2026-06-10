package com.lqr.papermind.review.service;

import com.lqr.papermind.review.dto.LeaderReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewAssignmentService {

    List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request);

    List<ReviewAssignmentResponse> assignReviewersByLeader(UUID currentUserId, UUID groupId, UUID taskId, LeaderReviewAssignmentRequest request);

    List<ReviewAssignmentResponse> listAssignments(UUID taskId);

    ReviewAssignmentResponse submitAssignment(UUID currentUserId, UUID assignmentId);

    List<ReviewerLoadResponse> listReviewerLoads();
}
