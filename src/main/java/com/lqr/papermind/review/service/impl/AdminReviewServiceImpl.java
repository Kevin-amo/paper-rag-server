package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskDetailResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewAssignmentRoles;
import com.lqr.papermind.review.model.ReviewAssignmentStatuses;
import com.lqr.papermind.review.service.AdminReviewService;
import com.lqr.papermind.review.service.ReviewAssignmentService;
import com.lqr.papermind.review.service.ReviewConsensusService;
import com.lqr.papermind.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewTaskMapper taskMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewConsensusMapper consensusMapper;
    private final ReviewService reviewService;
    private final ReviewAssignmentService assignmentService;
    private final ReviewConsensusService consensusService;
    private final SysUserMapper userMapper;

    /**
     * 分页查询评审任务列表
     *
     * @param keyword 关键字，用于按标题模糊搜索，可为null
     * @param status  任务状态过滤条件，可为null
     * @param page    页码，从0开始
     * @param size    每页大小，最大100
     * @return 包含评审任务摘要信息的分页响应
     */
    @Override
    public PageResponse<AdminReviewTaskSummaryResponse> listTasks(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<AdminReviewTaskSummaryResponse> allItems = taskMapper.selectAdminTasks(blankToNull(keyword), statusFilter(status)).stream()
                .map(this::toSummaryResponse)
                .toList();
        long offset = (long) safePage * safeSize;
        if (offset >= allItems.size()) {
            return new PageResponse<>(List.of(), safePage, safeSize, allItems.size());
        }
        int fromIndex = (int) offset;
        int toIndex = Math.min(fromIndex + safeSize, allItems.size());
        return new PageResponse<>(allItems.subList(fromIndex, toIndex), safePage, safeSize, allItems.size());
    }

    /**
     * 获取评审任务详情
     *
     * @param taskId 评审任务ID
     * @return 包含任务信息、分配记录、提交报告及共识详情的响应对象
     */
    @Override
    public AdminReviewTaskDetailResponse getTask(UUID taskId) {
        ReviewConsensusResponse consensus = consensusService.getForTask(taskId);
        return new AdminReviewTaskDetailResponse(
                reviewService.getTask(null, true, taskId),
                assignmentService.listAssignments(taskId),
                consensus == null ? List.of() : consensus.submittedReports(),
                consensus
        );
    }

    /**
     * 为评审任务分配评审人员
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @param request        评审分配请求，包含分配详情
     * @return 更新后的评审分配列表
     */
    @Override
    public List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request) {
        return assignmentService.assignReviewers(taskId, operatorUserId, request);
    }

    /**
     * 查询所有评审人员的工作负载情况
     *
     * @return 评审人员负载信息列表
     */
    @Override
    public List<ReviewerLoadResponse> listReviewerLoads() {
        return assignmentService.listReviewerLoads();
    }

    /**
     * 重新计算评审共识
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @return 重新计算后的共识详情
     */
    @Override
    public ReviewConsensusResponse recalculateConsensus(UUID taskId, UUID operatorUserId) {
        return consensusService.recalculate(taskId, operatorUserId);
    }

    /**
     * 更新评审共识信息
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @param request        共识更新请求
     * @return 更新后的共识详情
     */
    @Override
    public ReviewConsensusResponse updateConsensus(UUID taskId, UUID operatorUserId, ReviewConsensusUpdateRequest request) {
        return consensusService.update(taskId, operatorUserId, request);
    }

    /**
     * 确认评审共识
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @return 确认后的共识详情
     */
    @Override
    public ReviewConsensusResponse confirmConsensus(UUID taskId, UUID operatorUserId) {
        return consensusService.confirm(taskId, operatorUserId);
    }

    /**
     * 将评审任务实体转换为管理员评审任务摘要响应对象
     *
     * @param task 评审任务实体
     * @return 评审任务摘要响应对象
     */
    private AdminReviewTaskSummaryResponse toSummaryResponse(ReviewTaskEntity task) {
        List<ReviewAssignmentEntity> assignments = assignmentMapper.selectByTaskId(task.getId());
        ReviewConsensusEntity consensus = consensusMapper.selectByTaskId(task.getId());
        UUID leadReviewerUserId = task.getLeaderUserId();
        if (leadReviewerUserId == null) {
            leadReviewerUserId = assignments.stream()
                    .filter(assignment -> !ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus()))
                    .filter(assignment -> ReviewAssignmentRoles.LEAD.equals(assignment.getRole()))
                    .map(ReviewAssignmentEntity::getReviewerUserId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        SysUser leadReviewer = leadReviewerUserId == null ? null : userMapper.selectById(leadReviewerUserId);
        OffsetDateTime dueAt = assignmentMapper.maxDueAtByTaskId(task.getId());
        return new AdminReviewTaskSummaryResponse(
                task.getId(),
                task.getDocumentId(),
                task.getSubmitterUserId(),
                task.getSourceId(),
                task.getTitle(),
                task.getStatus(),
                assignments.stream()
                        .filter(assignment -> !ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus()))
                        .count(),
                assignments.stream()
                        .filter(assignment -> ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus()))
                        .count(),
                leadReviewerUserId,
                leadReviewer == null ? null : leadReviewer.getUsername(),
                leadReviewer == null ? null : leadReviewer.getDisplayName(),
                dueAt,
                consensus == null ? null : consensus.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    /**
     * 将空字符串或空白字符串转换为null
     *
     * @param value 输入字符串
     * @return 处理后的字符串，若输入为空或空白则返回null
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 标准化状态过滤条件
     *
     * @param status 状态字符串
     * @return 处理后的状态字符串，转为大写格式，若输入为空或空白则返回null
     */
    private String statusFilter(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase();
    }
}
