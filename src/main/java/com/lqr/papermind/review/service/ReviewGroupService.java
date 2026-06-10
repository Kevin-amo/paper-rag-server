package com.lqr.papermind.review.service;

import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewBatchRequest;
import com.lqr.papermind.review.dto.ReviewBatchResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberUpdateRequest;
import com.lqr.papermind.review.dto.ReviewGroupRequest;
import com.lqr.papermind.review.dto.ReviewGroupResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewGroupService {

    PageResponse<ReviewBatchResponse> listBatches(int page, int size);

    ReviewBatchResponse createBatch(UUID operatorUserId, ReviewBatchRequest request);

    ReviewBatchResponse updateBatch(UUID batchId, ReviewBatchRequest request);

    List<ReviewGroupResponse> listGroups(UUID batchId);

    ReviewGroupResponse createGroup(UUID operatorUserId, ReviewGroupRequest request);

    ReviewGroupResponse updateGroup(UUID groupId, ReviewGroupRequest request);

    List<ReviewGroupMemberResponse> listGroupMembers(UUID groupId);

    List<ReviewGroupMemberResponse> replaceGroupMembers(UUID operatorUserId, UUID groupId, ReviewGroupMemberUpdateRequest request);

    List<ReviewGroupResponse> listLeaderGroups(UUID leaderUserId);

    List<ReviewGroupMemberResponse> listGroupMembersForLeader(UUID currentUserId, UUID groupId);

    List<AdminReviewTaskSummaryResponse> listUnassignedTasksForLeader(UUID currentUserId, UUID groupId);

    List<AdminReviewTaskSummaryResponse> listGroupTasksForLeader(UUID currentUserId, UUID groupId);
}