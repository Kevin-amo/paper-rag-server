package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysRoleMapper;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.review.audit.ReviewAuditService;
import com.lqr.papermind.review.dto.LeaderReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewGroupEntity;
import com.lqr.papermind.review.entity.ReviewGroupMemberEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMemberMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewAssignmentRoles;
import com.lqr.papermind.review.model.ReviewAssignmentStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import com.lqr.papermind.review.service.ReviewAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewAssignmentServiceImpl implements ReviewAssignmentService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String MEMBER_ROLE_REVIEWER = "REVIEWER";

    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewTaskMapper taskMapper;
    private final SysUserMapper userMapper;
    private final ReviewGroupMapper groupMapper;
    private final ReviewGroupMemberMapper memberMapper;
    private final SysRoleMapper roleMapper;
    private final ReviewAuditService reviewAuditService;

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
        List<UUID> reviewerIds = requireUniqueReviewerIds(request == null ? null : request.reviewerUserIds());
        UUID leadReviewerUserId = request.leadReviewerUserId();
        if (!new HashSet<>(reviewerIds).contains(leadReviewerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "组长必须在评审人列表中");
        }
        for (UUID reviewerId : reviewerIds) {
            if (assignmentMapper.selectActiveByTaskAndReviewer(taskId, reviewerId) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审任务已分配给该评审人");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<ReviewAssignmentEntity> assignments = reviewerIds.stream()
                .map(reviewerId -> newAssignment(taskId, task.getGroupId(), operatorUserId, reviewerId, leadReviewerUserId, request.dueAt(), now))
                .toList();
        assignments.forEach(assignmentMapper::insert);
        taskMapper.updateTaskStatus(taskId, ReviewTaskStatuses.ASSIGNED);
        reviewAuditService.append(
                taskId,
                operatorUserId,
                "ASSIGN_BY_ADMIN_OVERRIDE",
                "管理员兜底分配评审任务",
                assignmentBeforeSnapshot(task),
                assignmentAfterSnapshot(task, task.getGroupId(), operatorUserId, leadReviewerUserId, reviewerIds, assignments, request.dueAt()),
                Map.of("scope", "admin-override")
        );
        return assignments.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<ReviewAssignmentResponse> assignReviewersByLeader(UUID currentUserId, UUID groupId, UUID taskId, LeaderReviewAssignmentRequest request) {
        ReviewGroupEntity group = requireManagedGroup(currentUserId, groupId);
        ReviewTaskEntity task = taskMapper.selectByIdIncludingDeleted(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        if (!group.getId().equals(task.getGroupId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能分配本组任务");
        }
        if (!isPendingAssignmentStatus(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务不在待分配状态");
        }
        if (assignmentMapper.countActiveByTaskId(taskId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审任务已分配，不允许重复分配评审人");
        }
        List<UUID> reviewerIds = requireUniqueReviewerIds(request == null ? null : request.reviewerUserIds());
        for (UUID reviewerId : reviewerIds) {
            requireActiveGroupReviewer(groupId, reviewerId);
            if (assignmentMapper.selectActiveByTaskAndReviewer(taskId, reviewerId) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审任务已分配给该评审人");
            }
        }

        OffsetDateTime dueAt = request == null ? null : request.dueAt();
        OffsetDateTime now = OffsetDateTime.now();
        List<ReviewAssignmentEntity> assignments = reviewerIds.stream()
                .map(reviewerId -> newReviewerAssignment(taskId, groupId, currentUserId, reviewerId, dueAt, now))
                .toList();
        assignments.forEach(assignmentMapper::insert);
        taskMapper.markAssignedByLeader(taskId, groupId, currentUserId, currentUserId, reviewerIds.getFirst(), dueAt);
        reviewAuditService.append(
                taskId,
                currentUserId,
                "ASSIGN_BY_LEADER",
                "组长分配本组评审任务",
                assignmentBeforeSnapshot(task),
                assignmentAfterSnapshot(task, groupId, currentUserId, currentUserId, reviewerIds, assignments, dueAt),
                Map.of("scope", "review-leader")
        );
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
        if (ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审分配已取消，不能提交");
        }
        if (ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus())) {
            return toResponse(assignment);
        }
        Map<String, Object> beforeSnapshot = assignmentStatusSnapshot(assignment, null, null, null);
        assignmentMapper.updateStatus(assignmentId, ReviewAssignmentStatuses.SUBMITTED);
        long activeCount = assignmentMapper.countActiveByTaskId(assignment.getTaskId());
        long submittedCount = assignmentMapper.countSubmittedByTaskId(assignment.getTaskId());
        boolean allSubmitted = activeCount == submittedCount;
        if (allSubmitted) {
            taskMapper.updateTaskStatus(assignment.getTaskId(), ReviewTaskStatuses.SUBMITTED);
        }
        assignment.setStatus(ReviewAssignmentStatuses.SUBMITTED);
        assignment.setSubmittedAt(OffsetDateTime.now());
        assignment.setUpdatedAt(OffsetDateTime.now());
        reviewAuditService.append(
                assignment.getTaskId(),
                currentUserId,
                "SUBMIT_ASSIGNMENT",
                "提交个人评审任务",
                beforeSnapshot,
                assignmentStatusSnapshot(assignment, allSubmitted ? ReviewTaskStatuses.SUBMITTED : null, activeCount, submittedCount),
                Map.of("scope", "reviewer")
        );
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

    private ReviewGroupEntity requireManagedGroup(UUID currentUserId, UUID groupId) {
        ReviewGroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审小组不存在");
        }
        if (currentUserId == null || !currentUserId.equals(group.getLeaderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能管理自己负责的小组");
        }
        if (!STATUS_ACTIVE.equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审小组不可用");
        }
        return group;
    }

    private void requireActiveGroupReviewer(UUID groupId, UUID reviewerId) {
        ReviewGroupMemberEntity member = memberMapper.selectActiveByGroupAndUser(groupId, reviewerId);
        if (member == null || !MEMBER_ROLE_REVIEWER.equals(member.getMemberRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能分配给本组有效评审员");
        }
        SysUser user = userMapper.selectById(reviewerId);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus()) || !roleMapper.selectRoleCodesByUserId(reviewerId).contains(RoleCodes.REVIEWER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审员用户不可用");
        }
    }

    private List<UUID> requireUniqueReviewerIds(List<UUID> reviewerIds) {
        if (reviewerIds == null || reviewerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审人列表不能为空");
        }
        if (reviewerIds.stream().anyMatch(id -> id == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审人不能为空");
        }
        Set<UUID> uniqueReviewerIds = new HashSet<>(reviewerIds);
        if (uniqueReviewerIds.size() != reviewerIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审人不能重复");
        }
        return reviewerIds;
    }

    private boolean isPendingAssignmentStatus(String status) {
        return ReviewTaskStatuses.PENDING_ASSIGNMENT.equals(status) || ReviewTaskStatuses.PENDING.equals(status);
    }

    private ReviewAssignmentEntity newAssignment(UUID taskId,
                                                 UUID groupId,
                                                 UUID assignedByUserId,
                                                 UUID reviewerId,
                                                 UUID leadReviewerUserId,
                                                 OffsetDateTime dueAt,
                                                 OffsetDateTime now) {
        ReviewAssignmentEntity assignment = newReviewerAssignment(taskId, groupId, assignedByUserId, reviewerId, dueAt, now);
        assignment.setRole(reviewerId.equals(leadReviewerUserId) ? ReviewAssignmentRoles.LEAD : ReviewAssignmentRoles.REVIEWER);
        return assignment;
    }

    private ReviewAssignmentEntity newReviewerAssignment(UUID taskId,
                                                         UUID groupId,
                                                         UUID assignedByUserId,
                                                         UUID reviewerId,
                                                         OffsetDateTime dueAt,
                                                         OffsetDateTime now) {
        ReviewAssignmentEntity assignment = new ReviewAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setTaskId(taskId);
        assignment.setReviewerUserId(reviewerId);
        assignment.setGroupId(groupId);
        assignment.setAssignedByUserId(assignedByUserId);
        assignment.setRole(ReviewAssignmentRoles.REVIEWER);
        assignment.setStatus(ReviewAssignmentStatuses.ASSIGNED);
        assignment.setAssignedAt(now);
        assignment.setDueAt(dueAt);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        return assignment;
    }

    private Map<String, Object> assignmentBeforeSnapshot(ReviewTaskEntity task) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskStatus", task.getStatus());
        snapshot.put("groupId", task.getGroupId());
        snapshot.put("reviewerUserId", task.getReviewerUserId());
        snapshot.put("assignedByUserId", task.getAssignedByUserId());
        snapshot.put("leaderUserId", task.getLeaderUserId());
        snapshot.put("dueAt", task.getDueAt());
        return snapshot;
    }

    private Map<String, Object> assignmentAfterSnapshot(ReviewTaskEntity task,
                                                        UUID groupId,
                                                        UUID assignedByUserId,
                                                        UUID leaderUserId,
                                                        List<UUID> reviewerIds,
                                                        List<ReviewAssignmentEntity> assignments,
                                                        OffsetDateTime dueAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskStatus", ReviewTaskStatuses.ASSIGNED);
        snapshot.put("groupId", groupId);
        snapshot.put("reviewerUserId", reviewerIds.getFirst());
        snapshot.put("assignedByUserId", assignedByUserId);
        snapshot.put("leaderUserId", leaderUserId);
        snapshot.put("dueAt", dueAt);
        snapshot.put("reviewerUserIds", reviewerIds);
        snapshot.put("assignmentIds", assignments.stream().map(ReviewAssignmentEntity::getId).toList());
        snapshot.put("previousTaskStatus", task.getStatus());
        return snapshot;
    }

    private Map<String, Object> assignmentStatusSnapshot(ReviewAssignmentEntity assignment,
                                                         String taskStatus,
                                                         Long activeCount,
                                                         Long submittedCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("assignmentId", assignment.getId());
        snapshot.put("taskId", assignment.getTaskId());
        snapshot.put("reviewerUserId", assignment.getReviewerUserId());
        snapshot.put("groupId", assignment.getGroupId());
        snapshot.put("role", assignment.getRole());
        snapshot.put("assignmentStatus", assignment.getStatus());
        snapshot.put("taskStatus", taskStatus);
        snapshot.put("activeAssignmentCount", activeCount);
        snapshot.put("submittedAssignmentCount", submittedCount);
        snapshot.put("submittedAt", assignment.getSubmittedAt());
        return snapshot;
    }

    private ReviewAssignmentResponse toResponse(ReviewAssignmentEntity assignment) {
        SysUser reviewer = assignment == null || assignment.getReviewerUserId() == null
                ? null
                : userMapper.selectById(assignment.getReviewerUserId());
        return ReviewAssignmentResponse.from(assignment, reviewer);
    }
}
