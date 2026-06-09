package com.lqr.paperragserver.review.service.impl;

import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.security.RoleCodes;
import com.lqr.paperragserver.review.dto.ReviewAssignmentRequest;
import com.lqr.paperragserver.review.dto.ReviewAssignmentResponse;
import com.lqr.paperragserver.review.dto.ReviewerLoadResponse;
import com.lqr.paperragserver.review.entity.ReviewAssignmentEntity;
import com.lqr.paperragserver.review.entity.ReviewTaskEntity;
import com.lqr.paperragserver.review.mapper.ReviewAssignmentMapper;
import com.lqr.paperragserver.review.mapper.ReviewTaskMapper;
import com.lqr.paperragserver.review.model.ReviewAssignmentRoles;
import com.lqr.paperragserver.review.model.ReviewAssignmentStatuses;
import com.lqr.paperragserver.review.model.ReviewTaskStatuses;
import com.lqr.paperragserver.review.service.ReviewAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewAssignmentServiceImpl implements ReviewAssignmentService {

    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewTaskMapper taskMapper;
    private final SysUserMapper userMapper;

    @Override
    @Transactional
    public List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request) {
        ReviewTaskEntity task = taskMapper.selectByIdIncludingDeleted(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        if (ReviewTaskStatuses.SUBMITTED.equals(task.getStatus())
                || ReviewTaskStatuses.COMPLETED.equals(task.getStatus())
                || ReviewTaskStatuses.CONSENSUS_CONFIRMED.equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务状态不允许重新分配评审人");
        }
        if (assignmentMapper.countActiveByTaskId(taskId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审任务已分配，不允许重复分配评审人");
        }
        List<UUID> reviewerIds = request == null ? null : request.reviewerUserIds();
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审人列表不能为空");
        }
        Set<UUID> uniqueReviewerIds = new HashSet<>(reviewerIds);
        if (uniqueReviewerIds.size() != reviewerIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审人不能重复");
        }
        UUID leadReviewerUserId = request.leadReviewerUserId();
        if (!uniqueReviewerIds.contains(leadReviewerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "组长必须在评审人列表中");
        }
        for (UUID reviewerId : reviewerIds) {
            if (assignmentMapper.selectByTaskAndReviewer(taskId, reviewerId) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审任务已分配给该评审人");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<ReviewAssignmentEntity> assignments = reviewerIds.stream()
                .map(reviewerId -> newAssignment(taskId, reviewerId, leadReviewerUserId, request.dueAt(), now))
                .toList();
        assignments.forEach(assignmentMapper::insert);
        taskMapper.updateTaskStatus(taskId, ReviewTaskStatuses.ASSIGNED);
        return assignments.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReviewAssignmentResponse> listAssignments(UUID taskId) {
        return assignmentMapper.selectByTaskId(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ReviewAssignmentResponse submitAssignment(UUID currentUserId, UUID assignmentId) {
        ReviewAssignmentEntity assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审分配不存在");
        }
        if (!assignment.getReviewerUserId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能提交自己的评审任务");
        }
        if (ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus())) {
            return toResponse(assignment);
        }
        assignmentMapper.updateStatus(assignmentId, ReviewAssignmentStatuses.SUBMITTED);
        long activeCount = assignmentMapper.countActiveByTaskId(assignment.getTaskId());
        long submittedCount = assignmentMapper.countSubmittedByTaskId(assignment.getTaskId());
        if (activeCount == submittedCount) {
            taskMapper.updateTaskStatus(assignment.getTaskId(), ReviewTaskStatuses.SUBMITTED);
        }
        assignment.setStatus(ReviewAssignmentStatuses.SUBMITTED);
        assignment.setSubmittedAt(OffsetDateTime.now());
        assignment.setUpdatedAt(OffsetDateTime.now());
        return toResponse(assignment);
    }

    @Override
    public List<ReviewerLoadResponse> listReviewerLoads() {
        return userMapper.selectActiveByRole(RoleCodes.REVIEWER).stream()
                .map(user -> new ReviewerLoadResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        assignmentMapper.countByReviewerAndStatus(user.getId(), ReviewAssignmentStatuses.ASSIGNED),
                        assignmentMapper.countByReviewerAndStatus(user.getId(), ReviewAssignmentStatuses.REVIEWING),
                        assignmentMapper.countByReviewerAndStatus(user.getId(), ReviewAssignmentStatuses.SUBMITTED)
                ))
                .toList();
    }

    private ReviewAssignmentEntity newAssignment(UUID taskId, UUID reviewerId, UUID leadReviewerUserId, OffsetDateTime dueAt, OffsetDateTime now) {
        ReviewAssignmentEntity assignment = new ReviewAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setTaskId(taskId);
        assignment.setReviewerUserId(reviewerId);
        assignment.setRole(reviewerId.equals(leadReviewerUserId) ? ReviewAssignmentRoles.LEAD : ReviewAssignmentRoles.REVIEWER);
        assignment.setStatus(ReviewAssignmentStatuses.ASSIGNED);
        assignment.setAssignedAt(now);
        assignment.setDueAt(dueAt);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        return assignment;
    }

    private ReviewAssignmentResponse toResponse(ReviewAssignmentEntity assignment) {
        SysUser reviewer = assignment == null || assignment.getReviewerUserId() == null
                ? null
                : userMapper.selectById(assignment.getReviewerUserId());
        return ReviewAssignmentResponse.from(assignment, reviewer);
    }
}
