package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.review.audit.ReviewAuditService;
import com.lqr.papermind.review.consensus.ConsensusCalculator;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewGroupEntity;
import com.lqr.papermind.review.entity.ReviewReportEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMapper;
import com.lqr.papermind.review.mapper.ReviewReportMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewConsensusStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewConsensusServiceImplTest {

    private final ReviewConsensusMapper consensusMapper = mock(ReviewConsensusMapper.class);
    private final ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
    private final ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
    private final ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
    private final ReviewGroupMapper groupMapper = mock(ReviewGroupMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final ReviewAuditService reviewAuditService = mock(ReviewAuditService.class);
    private final ReviewConsensusServiceImpl service = new ReviewConsensusServiceImpl(
            consensusMapper,
            reportMapper,
            assignmentMapper,
            taskMapper,
            groupMapper,
            userMapper,
            new ConsensusCalculator(),
            reviewAuditService
    );

    @Test
    void recalculateShouldCreateDraftConsensusWithAverageScore() {
        UUID taskId = UUID.randomUUID();
        UUID leadUserId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewAssignmentEntity lead = assignment(taskId, leadUserId);
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of(
                report(taskId, reviewerId, 80, "建议通过"),
                report(taskId, UUID.randomUUID(), 90, "建议修改后通过")
        ));
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(null);
        when(assignmentMapper.selectLeadByTaskId(taskId)).thenReturn(lead);
        when(userMapper.selectById(reviewerId)).thenReturn(user(reviewerId, "reviewer-a", "评审员A"));
        markAllSubmitted(taskId, 2L);

        ReviewConsensusResponse response = service.recalculate(taskId);

        ArgumentCaptor<ReviewConsensusEntity> captor = ArgumentCaptor.forClass(ReviewConsensusEntity.class);
        verify(consensusMapper).insert(captor.capture());
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
        ReviewConsensusEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTaskId()).isEqualTo(taskId);
        assertThat(saved.getLeadReviewerUserId()).isEqualTo(leadUserId);
        assertThat(saved.getStatus()).isEqualTo(ReviewConsensusStatuses.DRAFT);
        assertThat(saved.getFinalScore()).isEqualTo(85);
        assertThat(saved.getScoreSummary()).isNotNull();
        assertThat(saved.getCommentSummary()).isNotNull();
        assertThat(saved.getDisagreementItems()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(response.finalScore()).isEqualTo(85);
        assertThat(response.submittedReports()).hasSize(2);
        assertThat(response.submittedReports().getFirst().reviewerUsername()).isEqualTo("reviewer-a");
        assertThat(response.submittedReports().getFirst().reviewerDisplayName()).isEqualTo("评审员A");
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), isNull(), eq("RECALCULATE_CONSENSUS"), eq("重新计算最终评分与共识草稿"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue()).containsEntry("status", "MISSING");
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("leadReviewerUserId", leadUserId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("finalScore", 85);
        assertThat(afterSnapshotCaptor.getValue().get("id")).isNotNull();
        assertThat(clientInfoCaptor.getValue())
                .containsEntry("scope", "legacy")
                .containsEntry("reportCount", 2)
                .containsEntry("creating", true);
    }
    @Test
    void recalculateShouldPreferTaskLeaderSnapshotOverLegacyLeadAssignment() {
        UUID taskId = UUID.randomUUID();
        UUID operatorUserId = UUID.randomUUID();
        UUID currentLeaderId = UUID.randomUUID();
        UUID legacyLeadId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewTaskEntity task = task(taskId, UUID.randomUUID(), currentLeaderId);
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of(
                report(taskId, reviewerId, 88, "建议通过")
        ));
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(null);
        markAllSubmitted(taskId, 1L);

        ReviewConsensusResponse response = service.recalculate(taskId, operatorUserId);

        ArgumentCaptor<ReviewConsensusEntity> captor = ArgumentCaptor.forClass(ReviewConsensusEntity.class);
        verify(consensusMapper).insert(captor.capture());
        assertThat(captor.getValue().getLeadReviewerUserId()).isEqualTo(currentLeaderId);
        assertThat(response.leadReviewerUserId()).isEqualTo(currentLeaderId);
        verify(assignmentMapper, never()).selectLeadByTaskId(taskId);
    }



    @Test
    void recalculateShouldRejectConfirmedConsensus() {
        UUID taskId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        consensus.setStatus(ReviewConsensusStatuses.CONFIRMED);
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of(
                report(taskId, UUID.randomUUID(), 80, "pass")
        ));
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);

        assertThatThrownBy(() -> service.recalculate(taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("最终共识已确认，不能重新计算");
        verify(consensusMapper, never()).insert(any(ReviewConsensusEntity.class));
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
    }


    @Test
    void recalculateShouldRejectConfirmedConsensusEvenWithoutSubmittedReports() {
        UUID taskId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        consensus.setStatus(ReviewConsensusStatuses.CONFIRMED);
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.recalculate(taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("最终共识已确认，不能重新计算");
        verify(consensusMapper, never()).insert(any(ReviewConsensusEntity.class));
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
    }

    @Test
    void getForTaskShouldReturnNullWhenConsensusMissing() {
        UUID taskId = UUID.randomUUID();
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(null);

        ReviewConsensusResponse response = service.getForTask(taskId);

        assertThat(response).isNull();
    }

    @Test
    void updateShouldReturnNotFoundWhenConsensusMissing() {
        UUID taskId = UUID.randomUUID();
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(null);

        assertThatThrownBy(() -> service.update(taskId, new ReviewConsensusUpdateRequest(90, "ok")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404))
                .hasMessageContaining("共识汇总不存在，请先重新计算");
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
    }

    @Test
    void updateShouldChangeFinalScoreAndRecommendation() {
        UUID taskId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of());
        markAllSubmitted(taskId, 1L);

        ReviewConsensusResponse response = service.update(taskId, new ReviewConsensusUpdateRequest(92, "  建议通过  "));

        assertThat(response.finalScore()).isEqualTo(92);
        assertThat(response.finalRecommendation()).isEqualTo("建议通过");
        assertThat(consensus.getFinalScore()).isEqualTo(92);
        assertThat(consensus.getFinalRecommendation()).isEqualTo("建议通过");
        assertThat(consensus.getUpdatedAt()).isNotNull();
        verify(consensusMapper).updateById(consensus);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), isNull(), eq("UPDATE_CONSENSUS"), eq("保存最终评分与共识意见"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("finalScore", 80)
                .containsEntry("finalRecommendation", "建议修改后通过");
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("finalScore", 92)
                .containsEntry("finalRecommendation", "建议通过");
        assertThat(clientInfoCaptor.getValue()).containsEntry("scope", "legacy");
    }


    @Test
    void updateShouldRejectConfirmedConsensus() {
        UUID taskId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        consensus.setStatus(ReviewConsensusStatuses.CONFIRMED);
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);

        assertThatThrownBy(() -> service.update(taskId, new ReviewConsensusUpdateRequest(92, "pass")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("最终共识已确认，不能修改");
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
    }

    @Test
    void confirmShouldMarkConsensusConfirmedAndTaskConsensusConfirmed() {
        UUID taskId = UUID.randomUUID();
        UUID operatorUserId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        when(userMapper.selectById(operatorUserId)).thenReturn(user(operatorUserId, "admin", "系统管理员"));
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of());
        markAllSubmitted(taskId, 1L);

        ReviewConsensusResponse response = service.confirm(taskId, operatorUserId);

        assertThat(response.status()).isEqualTo(ReviewConsensusStatuses.CONFIRMED);
        assertThat(response.confirmedByUserId()).isEqualTo(operatorUserId);
        assertThat(response.confirmedByUsername()).isEqualTo("admin");
        assertThat(response.confirmedByDisplayName()).isEqualTo("系统管理员");
        assertThat(response.confirmedAt()).isNotNull();
        verify(consensusMapper).updateById(consensus);
        verify(taskMapper).updateTaskStatus(taskId, ReviewTaskStatuses.CONSENSUS_CONFIRMED);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), eq(operatorUserId), eq("CONFIRM_CONSENSUS"), eq("确认最终评分与共识意见"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("confirmedByUserId", null)
                .containsEntry("confirmedAt", null);
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.CONFIRMED)
                .containsEntry("confirmedByUserId", operatorUserId);
        assertThat(afterSnapshotCaptor.getValue().get("confirmedAt")).isNotNull();
        assertThat(clientInfoCaptor.getValue()).containsEntry("scope", "review-consensus");
    }

    @Test
    void confirmShouldRejectWhenAssignmentsStillPending() {
        UUID taskId = UUID.randomUUID();
        UUID operatorUserId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);
        when(assignmentMapper.countActiveByTaskId(taskId)).thenReturn(2L);
        when(assignmentMapper.countSubmittedByTaskId(taskId)).thenReturn(1L);

        assertThatThrownBy(() -> service.confirm(taskId, operatorUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("请等待所有评审人提交后再处理共识汇总");
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
        verify(taskMapper, never()).updateTaskStatus(any(UUID.class), any(String.class));
    }


    @Test
    void confirmShouldRejectConfirmedConsensus() {
        UUID taskId = UUID.randomUUID();
        UUID operatorUserId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        consensus.setStatus(ReviewConsensusStatuses.CONFIRMED);
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);

        assertThatThrownBy(() -> service.confirm(taskId, operatorUserId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("最终共识已确认，不能重复确认");
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
        verify(taskMapper, never()).updateTaskStatus(any(UUID.class), any(String.class));
    }

    @Test
    void canAccessConsensusAllowsAdminOrLeadOnly() {
        UUID taskId = UUID.randomUUID();
        UUID leadUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(assignmentMapper.selectLeadByTaskId(taskId)).thenReturn(assignment(taskId, leadUserId));

        assertThat(service.canAccessConsensus(otherUserId, true, taskId)).isTrue();
        assertThat(service.canAccessConsensus(leadUserId, false, taskId)).isTrue();
        assertThat(service.canAccessConsensus(otherUserId, false, taskId)).isFalse();
    }


    @Test
    void canAccessConsensusRejectsNullCurrentUser() {
        assertThat(service.canAccessConsensus(null, false, UUID.randomUUID())).isFalse();
    }

    @Test
    void recalculateShouldRejectWhenNoSubmittedReports() {
        UUID taskId = UUID.randomUUID();
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.recalculate(taskId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("没有已提交的个人评审报告");
        verify(consensusMapper, never()).insert(any(ReviewConsensusEntity.class));
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
    }

    @Test
    void canAccessConsensusAllowsActiveGroupLeaderBeforeLegacyLead() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID legacyLeadUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        ReviewTaskEntity task = task(taskId, groupId, leaderUserId);
        ReviewGroupEntity group = group(groupId, leaderUserId, "ACTIVE");
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(groupMapper.selectById(groupId)).thenReturn(group);
        when(assignmentMapper.selectLeadByTaskId(taskId)).thenReturn(assignment(taskId, legacyLeadUserId));

        assertThat(service.canAccessConsensus(otherUserId, true, taskId)).isTrue();
        assertThat(service.canAccessConsensus(leaderUserId, false, taskId)).isTrue();
        assertThat(service.canAccessConsensus(legacyLeadUserId, false, taskId)).isFalse();
        assertThat(service.canAccessConsensus(otherUserId, false, taskId)).isFalse();
    }

    @Test
    void listReportsForLeaderShouldReturnActiveAssignmentReportsOnly() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewReportEntity report = report(taskId, reviewerId, 88, "建议通过");
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, groupId, leaderUserId));
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderUserId, "ACTIVE"));
        when(reportMapper.selectActiveAssignmentReportsByTaskId(taskId)).thenReturn(List.of(report));
        when(userMapper.selectById(reviewerId)).thenReturn(user(reviewerId, "reviewer-a", "评审员A"));

        var reports = service.listReportsForLeader(leaderUserId, groupId, taskId);

        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst().reviewerUsername()).isEqualTo("reviewer-a");
        verify(reportMapper).selectActiveAssignmentReportsByTaskId(taskId);
    }

    @Test
    void listReportsForLeaderShouldRejectNonLeader() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, groupId, leaderUserId));
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderUserId, "ACTIVE"));

        assertThatThrownBy(() -> service.listReportsForLeader(otherUserId, groupId, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403))
                .hasMessageContaining("只能处理自己负责小组的最终评分");
        verify(reportMapper, never()).selectActiveAssignmentReportsByTaskId(taskId);
    }

    @Test
    void listReportsForLeaderShouldRejectTaskOutsideGroup() {
        UUID taskId = UUID.randomUUID();
        UUID requestedGroupId = UUID.randomUUID();
        UUID actualGroupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, actualGroupId, leaderUserId));

        assertThatThrownBy(() -> service.listReportsForLeader(leaderUserId, requestedGroupId, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403))
                .hasMessageContaining("只能处理本组评审任务");
        verify(reportMapper, never()).selectActiveAssignmentReportsByTaskId(taskId);
    }

    @Test
    void recalculateForLeaderShouldRejectDisabledGroup() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, groupId, leaderUserId));
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderUserId, "DISABLED"));

        assertThatThrownBy(() -> service.recalculateForLeader(leaderUserId, groupId, taskId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409))
                .hasMessageContaining("评审小组不可用");
        verify(consensusMapper, never()).insert(any(ReviewConsensusEntity.class));
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
    }

    @Test
    void recalculateForLeaderShouldUseCurrentGroupLeaderAsConsensusOwner() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, groupId, leaderUserId));
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderUserId, "ACTIVE"));
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(null);
        when(reportMapper.selectSubmittedActiveAssignmentReportsByTaskId(taskId)).thenReturn(List.of(report(taskId, reviewerId, 90, "建议通过")));
        markAllSubmitted(taskId, 1L);

        ReviewConsensusResponse response = service.recalculateForLeader(leaderUserId, groupId, taskId);

        ArgumentCaptor<ReviewConsensusEntity> captor = ArgumentCaptor.forClass(ReviewConsensusEntity.class);
        verify(consensusMapper).insert(captor.capture());
        assertThat(captor.getValue().getLeadReviewerUserId()).isEqualTo(leaderUserId);
        assertThat(response.leadReviewerUserId()).isEqualTo(leaderUserId);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), eq(leaderUserId), eq("RECALCULATE_CONSENSUS"), eq("重新计算最终评分与共识草稿"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue()).containsEntry("status", "MISSING");
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("leadReviewerUserId", leaderUserId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("finalScore", 90);
        assertThat(clientInfoCaptor.getValue())
                .containsEntry("scope", "review-consensus")
                .containsEntry("reportCount", 1)
                .containsEntry("creating", true);
    }

    @Test
    void updateForLeaderShouldRejectNonLeader() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, groupId, leaderUserId));
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderUserId, "ACTIVE"));

        assertThatThrownBy(() -> service.updateForLeader(otherUserId, groupId, taskId, new ReviewConsensusUpdateRequest(91, "ok")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403));
        verify(consensusMapper, never()).updateById(any(ReviewConsensusEntity.class));
    }

    @Test
    void updateForLeaderShouldSaveConsensusAndAuditLog() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, groupId, leaderUserId));
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderUserId, "ACTIVE"));
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);
        when(reportMapper.selectSubmittedByTaskId(taskId)).thenReturn(List.of());

        ReviewConsensusResponse response = service.updateForLeader(leaderUserId, groupId, taskId, new ReviewConsensusUpdateRequest(91, "  组长建议通过  "));

        assertThat(response.finalScore()).isEqualTo(91);
        assertThat(response.finalRecommendation()).isEqualTo("组长建议通过");
        verify(consensusMapper).updateById(consensus);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), eq(leaderUserId), eq("UPDATE_CONSENSUS"), eq("保存最终评分与共识意见"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("finalScore", 80)
                .containsEntry("finalRecommendation", "建议修改后通过");
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("finalScore", 91)
                .containsEntry("finalRecommendation", "组长建议通过");
        assertThat(clientInfoCaptor.getValue()).containsEntry("scope", "review-consensus");
    }

    @Test
    void confirmForLeaderShouldConfirmWhenAllAssignmentsSubmitted() {
        UUID taskId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID leaderUserId = UUID.randomUUID();
        ReviewConsensusEntity consensus = consensus(taskId);
        when(taskMapper.selectById(taskId)).thenReturn(task(taskId, groupId, leaderUserId));
        when(groupMapper.selectById(groupId)).thenReturn(group(groupId, leaderUserId, "ACTIVE"));
        when(consensusMapper.selectByTaskId(taskId)).thenReturn(consensus);
        markAllSubmitted(taskId, 1L);

        ReviewConsensusResponse response = service.confirmForLeader(leaderUserId, groupId, taskId);

        assertThat(response.status()).isEqualTo(ReviewConsensusStatuses.CONFIRMED);
        assertThat(response.confirmedByUserId()).isEqualTo(leaderUserId);
        verify(taskMapper).updateTaskStatus(taskId, ReviewTaskStatuses.CONSENSUS_CONFIRMED);
        ArgumentCaptor<Map<String, Object>> beforeSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> afterSnapshotCaptor = mapCaptor();
        ArgumentCaptor<Map<String, Object>> clientInfoCaptor = mapCaptor();
        verify(reviewAuditService).append(eq(taskId), eq(leaderUserId), eq("CONFIRM_CONSENSUS"), eq("确认最终评分与共识意见"), beforeSnapshotCaptor.capture(), afterSnapshotCaptor.capture(), clientInfoCaptor.capture());
        assertThat(beforeSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.DRAFT)
                .containsEntry("confirmedByUserId", null)
                .containsEntry("confirmedAt", null);
        assertThat(afterSnapshotCaptor.getValue())
                .containsEntry("taskId", taskId)
                .containsEntry("status", ReviewConsensusStatuses.CONFIRMED)
                .containsEntry("confirmedByUserId", leaderUserId);
        assertThat(afterSnapshotCaptor.getValue().get("confirmedAt")).isNotNull();
        assertThat(clientInfoCaptor.getValue()).containsEntry("scope", "review-consensus");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
    }

    private ReviewReportEntity report(UUID taskId, UUID reviewerUserId, int totalScore, String recommendation) {
        ReviewReportEntity report = new ReviewReportEntity();
        report.setId(UUID.randomUUID());
        report.setTaskId(taskId);
        report.setDocumentId(UUID.randomUUID());
        report.setReviewerUserId(reviewerUserId);
        report.setTotalScore(totalScore);
        report.setFinalRecommendation(recommendation);
        report.setStatus("CONFIRMED");
        return report;
    }

    private ReviewConsensusEntity consensus(UUID taskId) {
        ReviewConsensusEntity consensus = new ReviewConsensusEntity();
        consensus.setId(UUID.randomUUID());
        consensus.setTaskId(taskId);
        consensus.setStatus(ReviewConsensusStatuses.DRAFT);
        consensus.setFinalScore(80);
        consensus.setFinalRecommendation("建议修改后通过");
        return consensus;
    }

    private ReviewAssignmentEntity assignment(UUID taskId, UUID reviewerUserId) {
        ReviewAssignmentEntity assignment = new ReviewAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setTaskId(taskId);
        assignment.setReviewerUserId(reviewerUserId);
        assignment.setRole("LEAD");
        assignment.setStatus("ASSIGNED");
        return assignment;
    }

    private ReviewTaskEntity task(UUID taskId, UUID groupId, UUID leaderUserId) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setGroupId(groupId);
        task.setLeaderUserId(leaderUserId);
        return task;
    }

    private ReviewGroupEntity group(UUID groupId, UUID leaderUserId, String status) {
        ReviewGroupEntity group = new ReviewGroupEntity();
        group.setId(groupId);
        group.setLeaderUserId(leaderUserId);
        group.setStatus(status);
        return group;
    }

    private SysUser user(UUID id, String username, String displayName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }

    private void markAllSubmitted(UUID taskId, long count) {
        when(assignmentMapper.countActiveByTaskId(taskId)).thenReturn(count);
        when(assignmentMapper.countSubmittedByTaskId(taskId)).thenReturn(count);
    }
}
