package com.lqr.paperragserver.review.service.impl;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.auth.security.RoleCodes;
import com.lqr.paperragserver.review.dto.ReviewAssignmentRequest;
import com.lqr.paperragserver.review.entity.ReviewAssignmentEntity;
import com.lqr.paperragserver.review.entity.ReviewTaskEntity;
import com.lqr.paperragserver.review.mapper.ReviewAssignmentMapper;
import com.lqr.paperragserver.review.mapper.ReviewTaskMapper;
import com.lqr.paperragserver.review.model.ReviewAssignmentStatuses;
import com.lqr.paperragserver.review.model.ReviewTaskStatuses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewAssignmentServiceImplTest {

    private ReviewAssignmentMapper assignmentMapper;
    private ReviewTaskMapper taskMapper;
    private SysUserMapper userMapper;
    private ReviewAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        assignmentMapper = mock(ReviewAssignmentMapper.class);
        taskMapper = mock(ReviewTaskMapper.class);
        userMapper = mock(SysUserMapper.class);
        service = new ReviewAssignmentServiceImpl(assignmentMapper, taskMapper, userMapper);
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

        org.assertj.core.api.Assertions.assertThat(loads).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(loads.getFirst().reviewerUserId()).isEqualTo(reviewerId);
        org.assertj.core.api.Assertions.assertThat(loads.getFirst().username()).isEqualTo("reviewer");
        org.assertj.core.api.Assertions.assertThat(loads.getFirst().assignedCount()).isEqualTo(2L);
    }

    @Test
    void listAssignmentsIncludesReviewerDisplayName() {
        UUID taskId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(assignmentMapper.selectByTaskId(taskId)).thenReturn(List.of(
                assignment(assignmentId, taskId, reviewerId, ReviewAssignmentStatuses.SUBMITTED)
        ));
        when(userMapper.selectById(reviewerId)).thenReturn(user(reviewerId, "reviewer-a", "评审员A"));

        var assignments = service.listAssignments(taskId);

        org.assertj.core.api.Assertions.assertThat(assignments).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(assignments.getFirst().reviewerUsername()).isEqualTo("reviewer-a");
        org.assertj.core.api.Assertions.assertThat(assignments.getFirst().reviewerDisplayName()).isEqualTo("评审员A");
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

    private ReviewAssignmentEntity assignment(UUID assignmentId, UUID taskId, UUID reviewerId, String status) {
        ReviewAssignmentEntity assignment = new ReviewAssignmentEntity();
        assignment.setId(assignmentId);
        assignment.setTaskId(taskId);
        assignment.setReviewerUserId(reviewerId);
        assignment.setStatus(status);
        return assignment;
    }

    private SysUser user(UUID id, String username, String displayName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }
}
