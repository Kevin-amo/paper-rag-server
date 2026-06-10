package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysRoleMapper;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.review.audit.ReviewAuditService;
import com.lqr.papermind.review.dto.LeaderReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewAssignmentServiceImplTest {

    private ReviewAssignmentMapper assignmentMapper;
    private ReviewTaskMapper taskMapper;
    private SysUserMapper userMapper;
    private ReviewGroupMapper groupMapper;
    private ReviewGroupMemberMapper memberMapper;
    private SysRoleMapper roleMapper;
    private ReviewAuditService reviewAuditService;
    private ReviewAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        assignmentMapper = mock(ReviewAssignmentMapper.class);
        taskMapper = mock(ReviewTaskMapper.class);
        userMapper = mock(SysUserMapper.class);
        groupMapper = mock(ReviewGroupMapper.class);
        memberMapper = mock(ReviewGroupMemberMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        reviewAuditService = mock(ReviewAuditService.class);
        service = new ReviewAssignmentServiceImpl(assignmentMapper, taskMapper, userMapper, groupMapper, memberMapper, roleMapper, reviewAuditService);
    }

    @Test
    void assignReviewersRequiresLeadInReviewerList() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId));

        ReviewAssignmentRequest request = new ReviewAssignmentRequest(List.of(reviewerId), leadId, null);

        assertThatThrownBy(() -> service.assignReviewers(taskId, UUID.randomUUID(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("组长必须在评审人列表中");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void assignReviewersRejectsDuplicateReviewerIds() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId));

        ReviewAssignmentRequest request = new ReviewAssignmentRequest(List.of(reviewerId, reviewerId), reviewerId, null);

        assertThatThrownBy(() -> service.assignReviewers(taskId, UUID.randomUUID(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("评审人不能重复");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void assignReviewersRejectsSubmittedTask() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, ReviewTaskStatuses.SUBMITTED));

        ReviewAssignmentRequest request = new ReviewAssignmentRequest(List.of(reviewerId), reviewerId, null);

        assertThatThrownBy(() -> service.assignReviewers(taskId, UUID.randomUUID(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("当前任务状态不允许重新分配评审人");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void assignReviewersRejectsAlreadyAssignedTask() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId));
        when(assignmentMapper.countActiveByTaskId(taskId)).thenReturn(1L);

        ReviewAssignmentRequest request = new ReviewAssignmentRequest(List.of(reviewerId), reviewerId, null);

        assertThatThrownBy(() -> service.assignReviewers(taskId, UUID.randomUUID(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("评审任务已分配");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void assignReviewersCreatesAdminOverrideAssignmentsAndAuditLog() {
        UUID adminId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerAId = UUID.randomUUID();
        UUID reviewerBId = UUID.randomUUID();
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(5);
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, groupId, ReviewTaskStatuses.PENDING_ASSIGNMENT));

        var assignments = service.assignReviewers(taskId, adminId, new ReviewAssignmentRequest(List.of(reviewerAId, reviewerBId), reviewerBId, dueAt));

        ArgumentCaptor<ReviewAssignmentEntity> assignmentCaptor = ArgumentCaptor.forClass(ReviewAssignmentEntity.class);
        verify(assignmentMapper, times(2)).insert(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getTaskId).containsOnly(taskId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getGroupId).containsOnly(groupId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getAssignedByUserId).containsOnly(adminId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getReviewerUserId).containsExactly(reviewerAId, reviewerBId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getRole).containsExactly(ReviewAssignmentRoles.REVIEWER, ReviewAssignmentRoles.LEAD);
        assertThat(assignments).hasSize(2);
        verify(taskMapper).updateTaskStatus(taskId, ReviewTaskStatuses.ASSIGNED);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), eq(adminId), eq("ASSIGN_BY_ADMIN_OVERRIDE"), eq("管理员兜底分配评审任务"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue()).containsEntry("taskStatus", ReviewTaskStatuses.PENDING_ASSIGNMENT);
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskStatus", ReviewTaskStatuses.ASSIGNED)
                .containsEntry("groupId", groupId)
                .containsEntry("assignedByUserId", adminId)
                .containsEntry("leaderUserId", reviewerBId)
                .containsEntry("reviewerUserId", reviewerAId)
                .containsEntry("reviewerUserIds", List.of(reviewerAId, reviewerBId))
                .containsEntry("dueAt", dueAt)
                .containsEntry("previousTaskStatus", ReviewTaskStatuses.PENDING_ASSIGNMENT);
        assertThat((List<?>) afterSnapshotCaptor.getValue().get("assignmentIds")).hasSize(2);
        assertThat(clientInfoCaptor.getValue()).containsEntry("scope", "admin-override");
    }

    @Test
    void leaderAssignReviewersCreatesGroupAssignmentsAndAuditLog() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerAId = UUID.randomUUID();
        UUID reviewerBId = UUID.randomUUID();
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(7);
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "ACTIVE"));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, groupId, ReviewTaskStatuses.PENDING_ASSIGNMENT));
        when(memberMapper.selectActiveByGroupAndUser(groupId, reviewerAId)).thenReturn(member(groupId, reviewerAId, "REVIEWER"));
        when(memberMapper.selectActiveByGroupAndUser(groupId, reviewerBId)).thenReturn(member(groupId, reviewerBId, "REVIEWER"));
        when(userMapper.selectById(reviewerAId)).thenReturn(user(reviewerAId, "reviewer-a", "评审员A", "ACTIVE"));
        when(userMapper.selectById(reviewerBId)).thenReturn(user(reviewerBId, "reviewer-b", "评审员B", "ACTIVE"));
        when(roleMapper.selectRoleCodesByUserId(reviewerAId)).thenReturn(List.of(RoleCodes.REVIEWER));
        when(roleMapper.selectRoleCodesByUserId(reviewerBId)).thenReturn(List.of(RoleCodes.REVIEWER));

        var assignments = service.assignReviewersByLeader(leaderId, groupId, taskId, new LeaderReviewAssignmentRequest(List.of(reviewerAId, reviewerBId), dueAt));

        ArgumentCaptor<ReviewAssignmentEntity> assignmentCaptor = ArgumentCaptor.forClass(ReviewAssignmentEntity.class);
        verify(assignmentMapper, times(2)).insert(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getTaskId).containsOnly(taskId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getGroupId).containsOnly(groupId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getAssignedByUserId).containsOnly(leaderId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getReviewerUserId).containsExactly(reviewerAId, reviewerBId);
        assertThat(assignmentCaptor.getAllValues()).extracting(ReviewAssignmentEntity::getRole).containsOnly(ReviewAssignmentRoles.REVIEWER);
        assertThat(assignments).hasSize(2);
        verify(taskMapper).markAssignedByLeader(taskId, groupId, leaderId, leaderId, reviewerAId, dueAt);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), eq(leaderId), eq("ASSIGN_BY_LEADER"), eq("组长分配本组评审任务"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue()).containsEntry("taskStatus", ReviewTaskStatuses.PENDING_ASSIGNMENT);
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskStatus", ReviewTaskStatuses.ASSIGNED)
                .containsEntry("groupId", groupId)
                .containsEntry("assignedByUserId", leaderId)
                .containsEntry("leaderUserId", leaderId)
                .containsEntry("reviewerUserId", reviewerAId)
                .containsEntry("reviewerUserIds", List.of(reviewerAId, reviewerBId))
                .containsEntry("dueAt", dueAt)
                .containsEntry("previousTaskStatus", ReviewTaskStatuses.PENDING_ASSIGNMENT);
        assertThat((List<?>) afterSnapshotCaptor.getValue().get("assignmentIds")).hasSize(2);
        assertThat(clientInfoCaptor.getValue()).containsEntry("scope", "review-leader");
    }

    @Test
    void leaderAssignReviewersRejectsOtherGroupLeader() {
        UUID currentUserId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID otherLeaderId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, otherLeaderId, "ACTIVE"));

        assertThatThrownBy(() -> service.assignReviewersByLeader(currentUserId, groupId, UUID.randomUUID(), new LeaderReviewAssignmentRequest(List.of(UUID.randomUUID()), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("只能管理自己负责的小组");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void leaderAssignReviewersRejectsTaskOutsideGroup() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID otherGroupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "ACTIVE"));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, otherGroupId, ReviewTaskStatuses.PENDING_ASSIGNMENT));

        assertThatThrownBy(() -> service.assignReviewersByLeader(leaderId, groupId, taskId, new LeaderReviewAssignmentRequest(List.of(UUID.randomUUID()), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("只能分配本组任务");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void leaderAssignReviewersRejectsNonPendingTask() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "ACTIVE"));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, groupId, ReviewTaskStatuses.ASSIGNED));

        assertThatThrownBy(() -> service.assignReviewersByLeader(leaderId, groupId, taskId, new LeaderReviewAssignmentRequest(List.of(UUID.randomUUID()), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("当前任务不在待分配状态");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void leaderAssignReviewersRejectsReviewerOutsideGroup() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "ACTIVE"));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, groupId, ReviewTaskStatuses.PENDING_ASSIGNMENT));

        assertThatThrownBy(() -> service.assignReviewersByLeader(leaderId, groupId, taskId, new LeaderReviewAssignmentRequest(List.of(reviewerId), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("只能分配给本组有效评审员");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
    }

    @Test
    void leaderAssignReviewersRejectsDisabledGroup() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "DISABLED"));

        assertThatThrownBy(() -> service.assignReviewersByLeader(leaderId, groupId, UUID.randomUUID(), new LeaderReviewAssignmentRequest(List.of(UUID.randomUUID()), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("评审小组不可用");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
        verify(reviewAuditService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void leaderAssignReviewersRejectsLeaderMemberAsReviewer() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "ACTIVE"));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, groupId, ReviewTaskStatuses.PENDING_ASSIGNMENT));
        when(memberMapper.selectActiveByGroupAndUser(groupId, reviewerId)).thenReturn(member(groupId, reviewerId, "LEADER"));

        assertThatThrownBy(() -> service.assignReviewersByLeader(leaderId, groupId, taskId, new LeaderReviewAssignmentRequest(List.of(reviewerId), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("只能分配给本组有效评审员");
        verify(userMapper, never()).selectById(reviewerId);
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
        verify(reviewAuditService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void leaderAssignReviewersRejectsDisabledReviewerUser() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "ACTIVE"));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, groupId, ReviewTaskStatuses.PENDING_ASSIGNMENT));
        when(memberMapper.selectActiveByGroupAndUser(groupId, reviewerId)).thenReturn(member(groupId, reviewerId, "REVIEWER"));
        when(userMapper.selectById(reviewerId)).thenReturn(user(reviewerId, "reviewer-a", "评审员A", "DISABLED"));

        assertThatThrownBy(() -> service.assignReviewersByLeader(leaderId, groupId, taskId, new LeaderReviewAssignmentRequest(List.of(reviewerId), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("评审员用户不可用");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
        verify(reviewAuditService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void leaderAssignReviewersRejectsReviewerWithoutReviewerRole() {
        UUID leaderId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderId, "ACTIVE"));
        when(taskMapper.selectByIdIncludingDeleted(taskId)).thenReturn(task(taskId, groupId, ReviewTaskStatuses.PENDING_ASSIGNMENT));
        when(memberMapper.selectActiveByGroupAndUser(groupId, reviewerId)).thenReturn(member(groupId, reviewerId, "REVIEWER"));
        when(userMapper.selectById(reviewerId)).thenReturn(user(reviewerId, "reviewer-a", "评审员A", "ACTIVE"));
        when(roleMapper.selectRoleCodesByUserId(reviewerId)).thenReturn(List.of(RoleCodes.USER));

        assertThatThrownBy(() -> service.assignReviewersByLeader(leaderId, groupId, taskId, new LeaderReviewAssignmentRequest(List.of(reviewerId), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("评审员用户不可用");
        verify(assignmentMapper, never()).insert(any(ReviewAssignmentEntity.class));
        verify(reviewAuditService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitAssignmentRejectsOtherReviewer() {
        UUID assignmentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(assignmentMapper.selectById(assignmentId)).thenReturn(assignment(assignmentId, UUID.randomUUID(), reviewerId, ReviewAssignmentStatuses.ASSIGNED));

        assertThatThrownBy(() -> service.submitAssignment(otherUserId, assignmentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("只能提交自己的评审任务");
        verify(assignmentMapper, never()).updateStatus(any(), any());
    }

    @Test
    void submitAssignmentRejectsCancelledAssignment() {
        UUID assignmentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(assignmentMapper.selectById(assignmentId)).thenReturn(assignment(assignmentId, UUID.randomUUID(), reviewerId, ReviewAssignmentStatuses.CANCELLED));

        assertThatThrownBy(() -> service.submitAssignment(reviewerId, assignmentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("评审分配已取消");
        verify(assignmentMapper, never()).updateStatus(any(), any());
        verify(taskMapper, never()).updateTaskStatus(any(), any());
        verify(reviewAuditService, never()).append(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitAssignmentMovesTaskToSubmittedWhenAllAssignmentsSubmitted() {
        UUID taskId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewAssignmentEntity assignment = assignment(assignmentId, taskId, reviewerId, ReviewAssignmentStatuses.ASSIGNED);
        when(assignmentMapper.selectById(assignmentId)).thenReturn(assignment);
        when(assignmentMapper.countActiveByTaskId(taskId)).thenReturn(2L);
        when(assignmentMapper.countSubmittedByTaskId(taskId)).thenReturn(2L);

        service.submitAssignment(reviewerId, assignmentId);

        verify(assignmentMapper).updateStatus(assignmentId, ReviewAssignmentStatuses.SUBMITTED);
        verify(taskMapper).updateTaskStatus(taskId, ReviewTaskStatuses.SUBMITTED);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), eq(reviewerId), eq("SUBMIT_ASSIGNMENT"), eq("提交个人评审任务"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue())
                .containsEntry("assignmentId", assignmentId)
                .containsEntry("taskId", taskId)
                .containsEntry("reviewerUserId", reviewerId)
                .containsEntry("assignmentStatus", ReviewAssignmentStatuses.ASSIGNED);
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("assignmentId", assignmentId)
                .containsEntry("taskId", taskId)
                .containsEntry("reviewerUserId", reviewerId)
                .containsEntry("assignmentStatus", ReviewAssignmentStatuses.SUBMITTED)
                .containsEntry("taskStatus", ReviewTaskStatuses.SUBMITTED)
                .containsEntry("activeAssignmentCount", 2L)
                .containsEntry("submittedAssignmentCount", 2L);
        assertThat(afterSnapshotCaptor.getValue().get("submittedAt")).isNotNull();
        assertThat(clientInfoCaptor.getValue()).containsEntry("scope", "reviewer");
    }

    @Test
    void listReviewerLoadsIncludesActiveReviewersWithoutAssignments() {
        UUID reviewerId = UUID.randomUUID();
        SysUser user = new SysUser();
        user.setId(reviewerId);
        user.setUsername("reviewer");
        user.setDisplayName("Reviewer");
        when(userMapper.selectActiveByRole(RoleCodes.REVIEWER)).thenReturn(List.of(user));
        when(assignmentMapper.countByReviewerAndStatus(reviewerId, ReviewAssignmentStatuses.ASSIGNED)).thenReturn(2L);

        var loads = service.listReviewerLoads();

        assertThat(loads).hasSize(1);
        assertThat(loads.getFirst().reviewerUserId()).isEqualTo(reviewerId);
        assertThat(loads.getFirst().username()).isEqualTo("reviewer");
        assertThat(loads.getFirst().assignedCount()).isEqualTo(2L);
    }

    @Test
    void listAssignmentsIncludesReviewerDisplayName() {
        UUID taskId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(assignmentMapper.selectByTaskId(taskId)).thenReturn(List.of(
                assignment(assignmentId, taskId, reviewerId, ReviewAssignmentStatuses.SUBMITTED)
        ));
        when(userMapper.selectById(reviewerId)).thenReturn(user(reviewerId, "reviewer-a", "评审员A", "ACTIVE"));

        var assignments = service.listAssignments(taskId);

        assertThat(assignments).hasSize(1);
        assertThat(assignments.getFirst().reviewerUsername()).isEqualTo("reviewer-a");
        assertThat(assignments.getFirst().reviewerDisplayName()).isEqualTo("评审员A");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private ReviewTaskEntity task(UUID taskId) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setStatus(ReviewTaskStatuses.PENDING);
        return task;
    }

    private ReviewTaskEntity task(UUID taskId, String status) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setStatus(status);
        return task;
    }

    private ReviewTaskEntity task(UUID taskId, UUID groupId, String status) {
        ReviewTaskEntity task = task(taskId, status);
        task.setGroupId(groupId);
        return task;
    }

    private ReviewAssignmentEntity assignment(UUID assignmentId, UUID taskId, UUID reviewerId, String status) {
        ReviewAssignmentEntity assignment = new ReviewAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setTaskId(taskId);
        assignment.setReviewerUserId(reviewerId);
        assignment.setStatus(status);
        return assignment;
    }

    private ReviewGroupEntity group(UUID groupId, UUID leaderId, String status) {
        ReviewGroupEntity group = new ReviewGroupEntity();
        group.setId(groupId);
        group.setLeaderUserId(leaderId);
        group.setStatus(status);
        return group;
    }

    private ReviewGroupMemberEntity member(UUID groupId, UUID userId, String memberRole) {
        ReviewGroupMemberEntity member = new ReviewGroupMemberEntity();
        member.setId(UUID.randomUUID());
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setMemberRole(memberRole);
        member.setStatus("ACTIVE");
        return member;
    }

    private SysUser user(UUID id, String username, String displayName, String status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus(status);
        return user;
    }
}
