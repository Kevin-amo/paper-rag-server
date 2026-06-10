package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewAssignmentRoles;
import com.lqr.papermind.review.model.ReviewAssignmentStatuses;
import com.lqr.papermind.review.model.ReviewConsensusStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import com.lqr.papermind.review.service.ReviewAssignmentService;
import com.lqr.papermind.review.service.ReviewConsensusService;
import com.lqr.papermind.review.service.ReviewService;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminReviewServiceImplTest {

    private ReviewTaskMapper taskMapper;
    private ReviewAssignmentMapper assignmentMapper;
    private ReviewConsensusMapper consensusMapper;
    private ReviewService reviewService;
    private ReviewAssignmentService assignmentService;
    private ReviewConsensusService consensusService;
    private SysUserMapper userMapper;
    private AdminReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(ReviewTaskMapper.class);
        assignmentMapper = mock(ReviewAssignmentMapper.class);
        consensusMapper = mock(ReviewConsensusMapper.class);
        reviewService = mock(ReviewService.class);
        assignmentService = mock(ReviewAssignmentService.class);
        consensusService = mock(ReviewConsensusService.class);
        userMapper = mock(SysUserMapper.class);
        service = new AdminReviewServiceImpl(
                taskMapper,
                assignmentMapper,
                consensusMapper,
                reviewService,
                assignmentService,
                consensusService,
                userMapper
        );
    }

    @Test
    void listTasksPrefersTaskLeaderSnapshotOverLegacyLeadAssignmentForNewFlow() {
        UUID taskId = UUID.randomUUID();
        UUID currentLeaderId = UUID.randomUUID();
        UUID legacyLeadId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(3);
        ReviewTaskEntity task = task(taskId, ReviewTaskStatuses.SUBMITTED);
        task.setLeaderUserId(currentLeaderId);
        when(taskMapper.selectAdminTasks(null, null)).thenReturn(List.of(task));
        when(assignmentMapper.selectByTaskId(taskId)).thenReturn(List.of(
                assignment(taskId, legacyLeadId, ReviewAssignmentRoles.LEAD, ReviewAssignmentStatuses.SUBMITTED),
                assignment(taskId, reviewerId, ReviewAssignmentRoles.REVIEWER, ReviewAssignmentStatuses.SUBMITTED),
                assignment(taskId, UUID.randomUUID(), ReviewAssignmentRoles.REVIEWER, ReviewAssignmentStatuses.CANCELLED)
        ));
        when(assignmentMapper.maxDueAtByTaskId(taskId)).thenReturn(dueAt);
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus(taskId, ReviewConsensusStatuses.DRAFT));
        when(userMapper.selectById(currentLeaderId)).thenReturn(user(currentLeaderId, "leader-a", "当前组长"));

        var response = service.listTasks(null, null, 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        var item = response.items().getFirst();
        assertThat(item.leadReviewerUserId()).isEqualTo(currentLeaderId);
        assertThat(item.leadReviewerUsername()).isEqualTo("leader-a");
        assertThat(item.leadReviewerDisplayName()).isEqualTo("当前组长");
        assertThat(item.assignmentCount()).isEqualTo(2);
        assertThat(item.submittedCount()).isEqualTo(2);
        assertThat(item.dueAt()).isEqualTo(dueAt);
        assertThat(item.consensusStatus()).isEqualTo(ReviewConsensusStatuses.DRAFT);
        verify(userMapper).selectById(currentLeaderId);
        verify(userMapper, never()).selectById(legacyLeadId);
    }

    @Test
    void listTasksFallsBackToLegacyLeadAssignmentForOldTaskWithoutGroupLeaderSnapshot() {
        UUID taskId = UUID.randomUUID();
        UUID legacyLeadId = UUID.randomUUID();
        ReviewTaskEntity task = task(taskId, ReviewTaskStatuses.SUBMITTED);
        when(taskMapper.selectAdminTasks(null, null)).thenReturn(List.of(task));
        when(assignmentMapper.selectByTaskId(taskId)).thenReturn(List.of(
                assignment(taskId, legacyLeadId, ReviewAssignmentRoles.LEAD, ReviewAssignmentStatuses.SUBMITTED)
        ));
        when(userMapper.selectById(legacyLeadId)).thenReturn(user(legacyLeadId, "legacy-lead", "旧负责人"));

        var response = service.listTasks(null, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        var item = response.items().getFirst();
        assertThat(item.leadReviewerUserId()).isEqualTo(legacyLeadId);
        assertThat(item.leadReviewerUsername()).isEqualTo("legacy-lead");
        assertThat(item.leadReviewerDisplayName()).isEqualTo("旧负责人");
        assertThat(item.assignmentCount()).isEqualTo(1);
        assertThat(item.submittedCount()).isEqualTo(1);
    }

    private ReviewTaskEntity task(UUID taskId, String status) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setDocumentId(UUID.randomUUID());
        task.setSubmitterUserId(UUID.randomUUID());
        task.setSourceId("source-1");
        task.setTitle("评审任务");
        task.setStatus(status);
        task.setCreatedAt(OffsetDateTime.now().minusDays(1));
        task.setUpdatedAt(OffsetDateTime.now());
        return task;
    }

    private ReviewAssignmentEntity assignment(UUID taskId, UUID reviewerUserId, String role, String status) {
        ReviewAssignmentEntity assignment = new ReviewAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setTaskId(taskId);
        assignment.setReviewerUserId(reviewerUserId);
        assignment.setRole(role);
        assignment.setStatus(status);
        assignment.setAssignedAt(OffsetDateTime.now().minusDays(1));
        assignment.setCreatedAt(OffsetDateTime.now().minusDays(1));
        assignment.setUpdatedAt(OffsetDateTime.now());
        return assignment;
    }

    private ReviewConsensusEntity consensus(UUID taskId, String status) {
        ReviewConsensusEntity consensus = new ReviewConsensusEntity();
        consensus.setId(UUID.randomUUID());
        consensus.setTaskId(taskId);
        consensus.setStatus(status);
        return consensus;
    }

    private SysUser user(UUID userId, String username, String displayName) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }
}