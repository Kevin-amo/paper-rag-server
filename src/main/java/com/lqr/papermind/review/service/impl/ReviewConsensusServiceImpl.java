package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.review.consensus.ConsensusCalculator;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewReportResponse;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewReportEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewReportMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewConsensusStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import com.lqr.papermind.review.service.ReviewConsensusService;
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
    private final SysUserMapper userMapper;
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "最终共识已确认，不能重新计算");
        }

        List<ReviewReportEntity> reports = reportMapper.selectSubmittedByTaskId(taskId);
        if (reports == null || reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有已提交的个人评审报告");
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "最终共识已确认，不能修改");
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "最终共识已确认，不能重复确认");
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请等待所有评审人提交后再处理共识汇总");
        }
    }

    private ReviewConsensusEntity requireConsensus(UUID taskId) {
        ReviewConsensusEntity consensus = consensusMapper.selectByTaskId(taskId);
        if (consensus == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "共识汇总不存在，请先重新计算");
        }
        return consensus;
    }

    private ReviewConsensusResponse toResponse(ReviewConsensusEntity consensus) {
        return toResponse(consensus, reportMapper.selectSubmittedByTaskId(consensus.getTaskId()));
    }

    private ReviewConsensusResponse toResponse(ReviewConsensusEntity consensus, List<ReviewReportEntity> reports) {
        List<ReviewReportResponse> submittedReports = reports == null
                ? List.of()
                : reports.stream().map(this::toReportResponse).toList();
        SysUser leadReviewer = consensus.getLeadReviewerUserId() == null ? null : userMapper.selectById(consensus.getLeadReviewerUserId());
        SysUser confirmedBy = consensus.getConfirmedByUserId() == null ? null : userMapper.selectById(consensus.getConfirmedByUserId());
        return ReviewConsensusResponse.from(
                consensus,
                submittedReports,
                username(leadReviewer),
                displayName(leadReviewer),
                username(confirmedBy),
                displayName(confirmedBy)
        );
    }

    private String username(SysUser user) {
        return user == null ? null : user.getUsername();
    }

    private String displayName(SysUser user) {
        return user == null ? null : user.getDisplayName();
    }

    private ReviewReportResponse toReportResponse(ReviewReportEntity report) {
        SysUser reviewer = report == null || report.getReviewerUserId() == null
                ? null
                : userMapper.selectById(report.getReviewerUserId());
        return ReviewReportResponse.from(report, reviewer);
    }
}
