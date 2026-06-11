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

    /**
     * 查询评审任务列表
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param keyword       搜索关键词
     * @param status        任务状态过滤
     * @param page          页码
     * @param size          每页大小
     * @return 评审任务分页列表
     */
    PageResponse<ReviewTaskResponse> listTasks(UUID currentUserId, boolean admin, String keyword, String status, int page, int size);

    /**
     * 获取评审任务详情
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 评审任务详情
     */
    ReviewTaskResponse getTask(UUID currentUserId, boolean admin, UUID taskId);

    /**
     * 创建评审任务
     *
     * @param currentUserId 当前用户ID
     * @param request       创建任务请求参数
     * @return 创建的评审任务
     */
    ReviewTaskResponse createTask(UUID currentUserId, ReviewTaskCreateRequest request);

    /**
     * 为已索引的评审文档创建任务
     *
     * @param ownerUserId 文档所有者用户ID
     * @param sourceId    文档来源ID
     */
    void createTaskForIndexedReviewDocument(UUID ownerUserId, String sourceId);

    /**
     * 生成AI评审报告
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return AI生成的评审报告
     */
    ReviewReportResponse generateAiReview(UUID currentUserId, boolean admin, UUID taskId);

    /**
     * 获取结构化解析结果
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 结构化解析响应
     */
    PaperStructuredParseResponse getStructuredParse(UUID currentUserId, boolean admin, UUID taskId);

    /**
     * 重新生成结构化解析结果
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 重新生成的结构化解析响应
     */
    PaperStructuredParseResponse regenerateStructuredParse(UUID currentUserId, boolean admin, UUID taskId);

    /**
     * 更新评审报告
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param reportId      报告ID
     * @param request       更新请求参数
     * @return 更新后的评审报告
     */
    ReviewReportResponse updateReport(UUID currentUserId, boolean admin, UUID reportId, ReviewReportUpdateRequest request);

    /**
     * 提交评审任务分配
     *
     * @param currentUserId 当前用户ID
     * @param assignmentId  分配ID
     * @return 提交后的分配响应
     */
    ReviewAssignmentResponse submitAssignment(UUID currentUserId, UUID assignmentId);

    /**
     * 获取评审共识
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 评审共识响应
     */
    ReviewConsensusResponse getConsensus(UUID currentUserId, boolean admin, UUID taskId);

    /**
     * 更新评审共识
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @param request       更新请求参数
     * @return 更新后的评审共识
     */
    ReviewConsensusResponse updateConsensus(UUID currentUserId, boolean admin, UUID taskId, ReviewConsensusUpdateRequest request);

    /**
     * 确认评审共识
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param taskId        任务ID
     * @return 确认后的评审共识
     */
    ReviewConsensusResponse confirmConsensus(UUID currentUserId, boolean admin, UUID taskId);

    /**
     * 查询风险项列表
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param reportId      报告ID
     * @return 风险项列表
     */
    List<ReviewRiskItemResponse> listRisks(UUID currentUserId, boolean admin, UUID reportId);

    /**
     * 更新风险项
     *
     * @param currentUserId 当前用户ID
     * @param admin         是否为管理员
     * @param riskId        风险项ID
     * @param request       更新请求参数
     * @return 更新后的风险项
     */
    ReviewRiskItemResponse updateRisk(UUID currentUserId, boolean admin, UUID riskId, ReviewRiskUpdateRequest request);

    /**
     * 查询评审标准列表
     *
     * @param includeDisabled 是否包含已禁用的标准
     * @return 评审标准列表
     */
    List<ReviewCriterionResponse> listCriteria(boolean includeDisabled);

    /**
     * 创建评审标准
     *
     * @param request 创建标准请求参数
     * @return 创建的评审标准
     */
    ReviewCriterionResponse createCriterion(ReviewCriterionRequest request);

    /**
     * 更新评审标准
     *
     * @param id      标准ID
     * @param request 更新标准请求参数
     * @return 更新后的评审标准
     */
    ReviewCriterionResponse updateCriterion(UUID id, ReviewCriterionRequest request);
}
