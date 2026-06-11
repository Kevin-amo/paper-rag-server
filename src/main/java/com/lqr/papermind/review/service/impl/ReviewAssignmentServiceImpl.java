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

    /**
     * 管理员兜底分配评审人
     *
     * @param taskId          评审任务ID
     * @param operatorUserId  操作人用户ID
     * @param request         评审分配请求，包含评审人ID列表、组长ID和截止时间
     * @return 分配结果列表
     */
    @Override
    @Transactional
    public List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request) {
        ReviewTaskEntity task = taskMapper.selectByIdIncludingDeleted(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        if (ReviewTaskStatuses.SUBMITTED.equals(task.getStatus())
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

    /**
     * 组长分配本组评审任务
     *
     * @param currentUserId 当前操作用户ID（组长）
     * @param groupId       评审小组ID
     * @param taskId        评审任务ID
     * @param request       组长评审分配请求，包含评审人ID列表和截止时间
     * @return 分配结果列表
     */
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

    /**
     * 查询评审任务的所有分配记录
     *
     * @param taskId 评审任务ID
     * @return 该任务的所有评审分配响应列表
     */
    @Override
    public List<ReviewAssignmentResponse> listAssignments(UUID taskId) {
        return assignmentMapper.selectByTaskId(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 评审人提交评审分配任务
     *
     * @param currentUserId 当前操作用户ID（评审人）
     * @param assignmentId  评审分配ID
     * @return 更新后的评审分配响应
     */
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

    /**
     * 查询所有评审人的工作负载情况
     *
     * @return 评审人负载信息列表，包含各状态下的分配数量
     */
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

    /**
     * 验证当前用户是否有权管理指定小组，并返回有效的评审小组实体
     *
     * @param currentUserId 当前操作用户ID
     * @param groupId       评审小组ID
     * @return 有效的评审小组实体
     */
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

    /**
     * 验证指定用户是否为有效的小组评审员
     *
     * @param groupId    评审小组ID
     * @param reviewerId 评审员用户ID
     */
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

    /**
     * 验证评审人ID列表非空且无重复项
     *
     * @param reviewerIds 评审人ID列表
     * @return 验证通过的评审人ID列表
     */
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

    /**
     * 判断任务状态是否为待分配状态
     *
     * @param status 任务状态
     * @return 如果状态为待分配则返回true
     */
    private boolean isPendingAssignmentStatus(String status) {
        return ReviewTaskStatuses.PENDING_ASSIGNMENT.equals(status);
    }

    /**
     * 创建评审分配实体，自动设置组长角色
     *
     * @param taskId              评审任务ID
     * @param groupId             评审小组ID
     * @param assignedByUserId    分配人用户ID
     * @param reviewerId          评审员用户ID
     * @param leadReviewerUserId  组长用户ID
     * @param dueAt               截止时间
     * @param now                 当前时间
     * @return 新创建的评审分配实体
     */
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

    /**
     * 创建评审员分配实体
     *
     * @param taskId           评审任务ID
     * @param groupId          评审小组ID
     * @param assignedByUserId 分配人用户ID
     * @param reviewerId       评审员用户ID
     * @param dueAt            截止时间
     * @param now              当前时间
     * @return 新创建的评审员分配实体
     */
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

    /**
     * 创建评审任务变更前的快照
     *
     * @param task 评审任务实体
     * @return 包含任务当前状态信息的快照Map
     */
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

    /**
     * 创建评审任务分配后的快照
     *
     * @param task             评审任务实体
     * @param groupId          评审小组ID
     * @param assignedByUserId 分配人用户ID
     * @param leaderUserId     组长用户ID
     * @param reviewerIds      评审人ID列表
     * @param assignments      评审分配实体列表
     * @param dueAt            截止时间
     * @return 包含分配后任务状态信息的快照Map
     */
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

    /**
     * 创建评审分配状态变更快照
     *
     * @param assignment      评审分配实体
     * @param taskStatus      任务状态
     * @param activeCount     活跃分配数量
     * @param submittedCount  已提交分配数量
     * @return 包含分配状态信息的快照Map
     */
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

    /**
     * 将评审分配实体转换为响应对象
     *
     * @param assignment 评审分配实体
     * @return 包含评审员信息的评审分配响应对象
     */
    private ReviewAssignmentResponse toResponse(ReviewAssignmentEntity assignment) {
        SysUser reviewer = assignment == null || assignment.getReviewerUserId() == null
                ? null
                : userMapper.selectById(assignment.getReviewerUserId());
        return ReviewAssignmentResponse.from(assignment, reviewer);
    }
}
