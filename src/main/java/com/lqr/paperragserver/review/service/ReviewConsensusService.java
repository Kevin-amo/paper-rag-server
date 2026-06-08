package com.lqr.paperragserver.review.service;

import com.lqr.paperragserver.review.dto.ReviewConsensusResponse;
import com.lqr.paperragserver.review.dto.ReviewConsensusUpdateRequest;

import java.util.UUID;

public interface ReviewConsensusService {

    ReviewConsensusResponse getForTask(UUID taskId);

    ReviewConsensusResponse recalculate(UUID taskId);

    ReviewConsensusResponse update(UUID taskId, ReviewConsensusUpdateRequest request);

    ReviewConsensusResponse confirm(UUID taskId, UUID operatorUserId);

    boolean canAccessConsensus(UUID currentUserId, boolean admin, UUID taskId);
}
