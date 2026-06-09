package com.lqr.paperragserver.review.service.impl;

import com.lqr.paperragserver.auth.entity.SysUser;
import com.lqr.paperragserver.auth.mapper.SysUserMapper;
import com.lqr.paperragserver.review.consensus.ConsensusCalculator;
import com.lqr.paperragserver.review.dto.ReviewConsensusResponse;
import com.lqr.paperragserver.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.paperragserver.review.entity.ReviewAssignmentEntity;
import com.lqr.paperragserver.review.entity.ReviewConsensusEntity;
import com.lqr.paperragserver.review.entity.ReviewReportEntity;
import com.lqr.paperragserver.review.mapper.ReviewAssignmentMapper;
import com.lqr.paperragserver.review.mapper.ReviewConsensusMapper;
import com.lqr.paperragserver.review.mapper.ReviewReportMapper;
import com.lqr.paperragserver.review.mapper.ReviewTaskMapper;
import com.lqr.paperragserver.review.model.ReviewConsensusStatuses;
import com.lqr.paperragserver.review.model.ReviewTaskStatuses;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewConsensusServiceImplTest {

    private final ReviewConsensusMapper consensusMapper = mock(ReviewConsensusMapper.class);
    private final ReviewReportMapper reportMapper = mock(ReviewReportMapper.class);
    private final ReviewAssignmentMapper assignmentMapper = mock(ReviewAssignmentMapper.class);
    private final ReviewTaskMapper taskMapper = mock(ReviewTaskMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final ReviewConsensusServiceImpl service = new ReviewConsensusServiceImpl(
            consensusMapper,
            reportMapper,
            assignmentMapper,
            taskMapper,
            userMapper,
            new ConsensusCalculator()
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
                .hasMessageContaining("\u6700\u7ec8\u5171\u8bc6\u5df2\u786e\u8ba4\uff0c\u4e0d\u80fd\u91cd\u65b0\u8ba1\u7b97");
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
                .hasMessageContaining("\u6700\u7ec8\u5171\u8bc6\u5df2\u786e\u8ba4\uff0c\u4e0d\u80fd\u91cd\u65b0\u8ba1\u7b97");
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
                .hasMessageContaining("\u5171\u8bc6\u6c47\u603b\u4e0d\u5b58\u5728\uff0c\u8bf7\u5148\u91cd\u65b0\u8ba1\u7b97");
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
                .hasMessageContaining("\u6700\u7ec8\u5171\u8bc6\u5df2\u786e\u8ba4\uff0c\u4e0d\u80fd\u4fee\u6539");
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
                .hasMessageContaining("\u8bf7\u7b49\u5f85\u6240\u6709\u8bc4\u5ba1\u4eba\u63d0\u4ea4\u540e\u518d\u5904\u7406\u5171\u8bc6\u6c47\u603b");
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
                .hasMessageContaining("\u6700\u7ec8\u5171\u8bc6\u5df2\u786e\u8ba4\uff0c\u4e0d\u80fd\u91cd\u590d\u786e\u8ba4");
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
