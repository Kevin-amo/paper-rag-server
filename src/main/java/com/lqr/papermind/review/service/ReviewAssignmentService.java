package com.lqr.papermind.review.service;

import com.lqr.papermind.review.dto.LeaderReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewAssignmentService {

    /**
     * 分配评审人
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @param request        分配请求参数
     * @return 分配结果列表
     */
    List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request);

    /**
     * 组长分配评审人
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @param taskId        任务ID
     * @param request       组长分配请求参数
     * @return 分配结果列表
     */
    List<ReviewAssignmentResponse> assignReviewersByLeader(UUID currentUserId, UUID groupId, UUID taskId, LeaderReviewAssignmentRequest request);

    /**
     * 查询任务的分配列表
     *
     * @param taskId 任务ID
     * @return 分配结果列表
     */
    List<ReviewAssignmentResponse> listAssignments(UUID taskId);

    /**
     * 提交评审任务分配
     *
     * @param currentUserId 当前用户ID
     * @param assignmentId  分配ID
     * @return 提交后的分配响应
     */
    ReviewAssignmentResponse submitAssignment(UUID currentUserId, UUID assignmentId);

    /**
     * 查询评审人工作负载列表
     *
     * @return 评审人工作负载列表
     */
    List<ReviewerLoadResponse> listReviewerLoads();
}
