package com.lqr.papermind.review.service;

import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewReportResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewConsensusService {

    ReviewConsensusResponse getForTask(UUID taskId);

    ReviewConsensusResponse recalculate(UUID taskId);

    ReviewConsensusResponse recalculate(UUID taskId, UUID operatorUserId);

    ReviewConsensusResponse update(UUID taskId, ReviewConsensusUpdateRequest request);

    ReviewConsensusResponse update(UUID taskId, UUID operatorUserId, ReviewConsensusUpdateRequest request);

    ReviewConsensusResponse confirm(UUID taskId, UUID operatorUserId);

    List<ReviewReportResponse> listReportsForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    ReviewConsensusResponse getForTaskForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    ReviewConsensusResponse recalculateForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    ReviewConsensusResponse updateForLeader(UUID currentUserId, UUID groupId, UUID taskId, ReviewConsensusUpdateRequest request);

    ReviewConsensusResponse confirmForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    boolean canAccessConsensus(UUID currentUserId, boolean admin, UUID taskId);
}
