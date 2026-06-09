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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "\u8bc4\u5ba1\u4efb\u52a1\u4e0d\u5b58\u5728");
        }
        if (ReviewTaskStatuses.SUBMITTED.equals(task.getStatus())
                || ReviewTaskStatuses.COMPLETED.equals(task.getStatus())
                || ReviewTaskStatuses.CONSENSUS_CONFIRMED.equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\u5f53\u524d\u4efb\u52a1\u72b6\u6001\u4e0d\u5141\u8bb8\u91cd\u65b0\u5206\u914d\u8bc4\u5ba1\u4eba");
        }
        if (assignmentMapper.countActiveByTaskId(taskId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\u8bc4\u5ba1\u4efb\u52a1\u5df2\u5206\u914d\uff0c\u4e0d\u5141\u8bb8\u91cd\u590d\u5206\u914d\u8bc4\u5ba1\u4eba");
        }
        List<UUID> reviewerIds = request == null ? null : request.reviewerUserIds();
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\u8bc4\u5ba1\u4eba\u5217\u8868\u4e0d\u80fd\u4e3a\u7a7a");
        }
        Set<UUID> uniqueReviewerIds = new HashSet<>(reviewerIds);
        if (uniqueReviewerIds.size() != reviewerIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\u8bc4\u5ba1\u4eba\u4e0d\u80fd\u91cd\u590d");
        }
        UUID leadReviewerUserId = request.leadReviewerUserId();
        if (!uniqueReviewerIds.contains(leadReviewerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\u7ec4\u957f\u5fc5\u987b\u5728\u8bc4\u5ba1\u4eba\u5217\u8868\u4e2d");
        }
        for (UUID reviewerId : reviewerIds) {
            if (assignmentMapper.selectByTaskAndReviewer(taskId, reviewerId) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\u8bc4\u5ba1\u4efb\u52a1\u5df2\u5206\u914d\u7ed9\u8be5\u8bc4\u5ba1\u4eba");
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "\u8bc4\u5ba1\u5206\u914d\u4e0d\u5b58\u5728");
        }
        if (!assignment.getReviewerUserId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "\u53ea\u80fd\u63d0\u4ea4\u81ea\u5df1\u7684\u8bc4\u5ba1\u4efb\u52a1");
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
