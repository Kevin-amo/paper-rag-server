package com.lqr.papermind.review.service;

import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewReportResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewConsensusService {

    /**
     * 获取任务的评审共识
     *
     * @param taskId 任务ID
     * @return 评审共识响应
     */
    ReviewConsensusResponse getForTask(UUID taskId);

    /**
     * 重新计算评审共识
     *
     * @param taskId 任务ID
     * @return 重新计算后的评审共识
     */
    ReviewConsensusResponse recalculate(UUID taskId);

    /**
     * 重新计算评审共识（指定操作人）
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @return 重新计算后的评审共识
     */
    ReviewConsensusResponse recalculate(UUID taskId, UUID operatorUserId);

    /**
     * 更新评审共识
     *
     * @param taskId  任务ID
     * @param request 更新请求参数
     * @return 更新后的评审共识
     */
    ReviewConsensusResponse update(UUID taskId, ReviewConsensusUpdateRequest request);

    /**
     * 更新评审共识（指定操作人）
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @param request        更新请求参数
     * @return 更新后的评审共识
     */
    ReviewConsensusResponse update(UUID taskId, UUID operatorUserId, ReviewConsensusUpdateRequest request);

    /**
     * 确认评审共识
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @return 确认后的评审共识
     */
    ReviewConsensusResponse confirm(UUID taskId, UUID operatorUserId);

    /**
     * 组长查询评审报告列表
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @param taskId        任务ID
     * @return 评审报告列表
     */
    List<ReviewReportResponse> listReportsForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    /**
     * 组长获取任务的评审共识
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @param taskId        任务ID
     * @return 评审共识响应
     */
    ReviewConsensusResponse getForTaskForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    /**
     * 组长重新计算评审共识
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @param taskId        任务ID
     * @return 重新计算后的评审共识
     */
    ReviewConsensusResponse recalculateForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    /**
     * 组长更新评审共识
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @param taskId        任务ID
     * @param request       更新请求参数
     * @return 更新后的评审共识
     */
    ReviewConsensusResponse updateForLeader(UUID currentUserId, UUID groupId, UUID taskId, ReviewConsensusUpdateRequest request);

    /**
     * 组长确认评审共识
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @param taskId        任务ID
     * @return 确认后的评审共识
     */
    ReviewConsensusResponse confirmForLeader(UUID currentUserId, UUID groupId, UUID taskId);

    /**
     * 检查用户是否有权限访问评审共识
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 是否有访问权限
     */
    boolean canAccessConsensus(UUID currentUserId, boolean admin, UUID taskId);
}
