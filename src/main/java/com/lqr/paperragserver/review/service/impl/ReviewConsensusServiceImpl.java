package com.lqr.paperragserver.review.service.impl;

import com.lqr.paperragserver.review.consensus.ConsensusCalculator;
import com.lqr.paperragserver.review.dto.ReviewConsensusResponse;
import com.lqr.paperragserver.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.paperragserver.review.dto.ReviewReportResponse;
import com.lqr.paperragserver.review.entity.ReviewAssignmentEntity;
import com.lqr.paperragserver.review.entity.ReviewConsensusEntity;
import com.lqr.paperragserver.review.entity.ReviewReportEntity;
import com.lqr.paperragserver.review.mapper.ReviewAssignmentMapper;
import com.lqr.paperragserver.review.mapper.ReviewConsensusMapper;
import com.lqr.paperragserver.review.mapper.ReviewReportMapper;
import com.lqr.paperragserver.review.mapper.ReviewTaskMapper;
import com.lqr.paperragserver.review.model.ReviewConsensusStatuses;
import com.lqr.paperragserver.review.model.ReviewTaskStatuses;
import com.lqr.paperragserver.review.service.ReviewConsensusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewConsensusServiceImpl implements ReviewConsensusService {

    private final ReviewConsensusMapper consensusMapper;
    private final ReviewReportMapper reportMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewTaskMapper taskMapper;
    private final ConsensusCalculator consensusCalculator;

    @Override
    public ReviewConsensusResponse getForTask(UUID taskId) {
        ReviewConsensusEntity entity = consensusMapper.selectByTaskId(taskId);
        if (entity == null) {
            return null;
        }
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ReviewConsensusResponse recalculate(UUID taskId) {
        ReviewConsensusEntity consensus = consensusMapper.selectByTaskId(taskId);
        if (isConfirmed(consensus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\u6700\u7ec8\u5171\u8bc6\u5df2\u786e\u8ba4\uff0c\u4e0d\u80fd\u91cd\u65b0\u8ba1\u7b97");
        }

        List<ReviewReportEntity> reports = reportMapper.selectSubmittedByTaskId(taskId);
        if (reports == null || reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\u6ca1\u6709\u5df2\u63d0\u4ea4\u7684\u4e2a\u4eba\u8bc4\u5ba1\u62a5\u544a");
        }
        requireAllAssignmentsSubmitted(taskId);

        ConsensusCalculator.Result result = consensusCalculator.calculate(reports);
        boolean creating = consensus == null;
        OffsetDateTime now = OffsetDateTime.now();
        if (creating) {
            consensus = new ReviewConsensusEntity();
            consensus.setId(UUID.randomUUID());
            consensus.setTaskId(taskId);
            consensus.setCreatedAt(now);
        }
        ReviewAssignmentEntity lead = assignmentMapper.selectLeadByTaskId(taskId);
        consensus.setLeadReviewerUserId(lead == null ? null : lead.getReviewerUserId());
        consensus.setScoreSummary(result.scoreSummary());
        consensus.setCommentSummary(result.commentSummary());
        consensus.setDisagreementItems(result.disagreementItems());
        consensus.setFinalScore(result.finalScore());
        consensus.setStatus(ReviewConsensusStatuses.DRAFT);
        consensus.setUpdatedAt(now);
        if (creating) {
            consensusMapper.insert(consensus);
        } else {
            consensusMapper.updateById(consensus);
        }
        return toResponse(consensus, reports);
    }

    @Override
    @Transactional
    public ReviewConsensusResponse update(UUID taskId, ReviewConsensusUpdateRequest request) {
        ReviewConsensusEntity consensus = requireConsensus(taskId);
        if (isConfirmed(consensus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\u6700\u7ec8\u5171\u8bc6\u5df2\u786e\u8ba4\uff0c\u4e0d\u80fd\u4fee\u6539");
        }
        if (request.finalScore() != null) {
            consensus.setFinalScore(request.finalScore());
        }
        if (request.finalRecommendation() != null) {
            consensus.setFinalRecommendation(request.finalRecommendation().trim());
        }
        consensus.setUpdatedAt(OffsetDateTime.now());
        consensusMapper.updateById(consensus);
        return toResponse(consensus);
    }

    @Override
    @Transactional
    public ReviewConsensusResponse confirm(UUID taskId, UUID operatorUserId) {
        ReviewConsensusEntity consensus = requireConsensus(taskId);
        if (isConfirmed(consensus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\u6700\u7ec8\u5171\u8bc6\u5df2\u786e\u8ba4\uff0c\u4e0d\u80fd\u91cd\u590d\u786e\u8ba4");
        }
        requireAllAssignmentsSubmitted(taskId);
        OffsetDateTime now = OffsetDateTime.now();
        consensus.setStatus(ReviewConsensusStatuses.CONFIRMED);
        consensus.setConfirmedByUserId(operatorUserId);
        consensus.setConfirmedAt(now);
        consensus.setUpdatedAt(now);
        consensusMapper.updateById(consensus);
        taskMapper.updateTaskStatus(taskId, ReviewTaskStatuses.CONSENSUS_CONFIRMED);
        return toResponse(consensus);
    }

    @Override
    public boolean canAccessConsensus(UUID currentUserId, boolean admin, UUID taskId) {
        if (admin) {
            return true;
        }
        if (currentUserId == null) {
            return false;
        }
        ReviewAssignmentEntity lead = assignmentMapper.selectLeadByTaskId(taskId);
        return lead != null && currentUserId.equals(lead.getReviewerUserId());
    }

    private boolean isConfirmed(ReviewConsensusEntity consensus) {
        return consensus != null && ReviewConsensusStatuses.CONFIRMED.equals(consensus.getStatus());
    }

    private void requireAllAssignmentsSubmitted(UUID taskId) {
        long activeCount = assignmentMapper.countActiveByTaskId(taskId);
        long submittedCount = assignmentMapper.countSubmittedByTaskId(taskId);
        if (activeCount == 0 || activeCount != submittedCount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "\u8bf7\u7b49\u5f85\u6240\u6709\u8bc4\u5ba1\u4eba\u63d0\u4ea4\u540e\u518d\u5904\u7406\u5171\u8bc6\u6c47\u603b");
        }
    }

    private ReviewConsensusEntity requireConsensus(UUID taskId) {
        ReviewConsensusEntity consensus = consensusMapper.selectByTaskId(taskId);
        if (consensus == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "\u5171\u8bc6\u6c47\u603b\u4e0d\u5b58\u5728\uff0c\u8bf7\u5148\u91cd\u65b0\u8ba1\u7b97");
        }
        return consensus;
    }

    private ReviewConsensusResponse toResponse(ReviewConsensusEntity consensus) {
        return toResponse(consensus, reportMapper.selectSubmittedByTaskId(consensus.getTaskId()));
    }

    private ReviewConsensusResponse toResponse(ReviewConsensusEntity consensus, List<ReviewReportEntity> reports) {
        List<ReviewReportResponse> submittedReports = reports == null
                ? List.of()
                : reports.stream().map(ReviewReportResponse::from).toList();
        return ReviewConsensusResponse.from(consensus, submittedReports);
    }
}
