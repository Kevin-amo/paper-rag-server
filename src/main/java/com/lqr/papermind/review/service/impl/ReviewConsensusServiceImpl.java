package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.review.audit.ReviewAuditService;
import com.lqr.papermind.review.consensus.ConsensusCalculator;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewReportResponse;
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
import com.lqr.papermind.review.service.ReviewConsensusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewConsensusServiceImpl implements ReviewConsensusService {

    private static final String GROUP_STATUS_ACTIVE = "ACTIVE";

    private final ReviewConsensusMapper consensusMapper;
    private final ReviewReportMapper reportMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewTaskMapper taskMapper;
    private final ReviewGroupMapper groupMapper;
    private final SysUserMapper userMapper;
    private final ConsensusCalculator consensusCalculator;
    private final ReviewAuditService reviewAuditService;

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
        return recalculate(taskId, null);
    }

    @Override
    @Transactional
    public ReviewConsensusResponse recalculate(UUID taskId, UUID operatorUserId) {
        return recalculate(taskId, operatorUserId, resolveConsensusLeadUserId(taskId), reportMapper.selectSubmittedByTaskId(taskId));
    }

    @Override
    @Transactional
    public ReviewConsensusResponse update(UUID taskId, ReviewConsensusUpdateRequest request) {
        return update(taskId, null, request);
    }

    @Override
    @Transactional
    public ReviewConsensusResponse update(UUID taskId, UUID operatorUserId, ReviewConsensusUpdateRequest request) {
        ReviewConsensusEntity consensus = requireConsensus(taskId);
        if (isConfirmed(consensus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "最终共识已确认，不能修改");
        }
        Map<String, Object> beforeSnapshot = consensusSnapshot(consensus);
        if (request.finalScore() != null) {
            consensus.setFinalScore(request.finalScore());
        }
        if (request.finalRecommendation() != null) {
            consensus.setFinalRecommendation(request.finalRecommendation().trim());
        }
        consensus.setUpdatedAt(OffsetDateTime.now());
        consensusMapper.updateById(consensus);
        reviewAuditService.append(
                taskId,
                operatorUserId,
                "UPDATE_CONSENSUS",
                "保存最终评分与共识意见",
                beforeSnapshot,
                consensusSnapshot(consensus),
                Map.of("scope", operatorUserId == null ? "legacy" : "review-consensus")
        );
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
        Map<String, Object> beforeSnapshot = consensusSnapshot(consensus);
        OffsetDateTime now = OffsetDateTime.now();
        consensus.setStatus(ReviewConsensusStatuses.CONFIRMED);
        consensus.setConfirmedByUserId(operatorUserId);
        consensus.setConfirmedAt(now);
        consensus.setUpdatedAt(now);
        consensusMapper.updateById(consensus);
        taskMapper.updateTaskStatus(taskId, ReviewTaskStatuses.CONSENSUS_CONFIRMED);
        reviewAuditService.append(
                taskId,
                operatorUserId,
                "CONFIRM_CONSENSUS",
                "确认最终评分与共识意见",
                beforeSnapshot,
                consensusSnapshot(consensus),
                Map.of("scope", operatorUserId == null ? "legacy" : "review-consensus")
        );
        return toResponse(consensus);
    }

    @Override
    public List<ReviewReportResponse> listReportsForLeader(UUID currentUserId, UUID groupId, UUID taskId) {
        requireTaskLeader(currentUserId, groupId, taskId);
        return reportMapper.selectActiveAssignmentReportsByTaskId(taskId).stream()
                .map(this::toReportResponse)
                .toList();
    }

    @Override
    public ReviewConsensusResponse getForTaskForLeader(UUID currentUserId, UUID groupId, UUID taskId) {
        requireTaskLeader(currentUserId, groupId, taskId);
        ReviewConsensusEntity entity = consensusMapper.selectByTaskId(taskId);
        if (entity == null) {
            return null;
        }
        return toResponse(entity, reportMapper.selectActiveAssignmentReportsByTaskId(taskId));
    }

    @Override
    @Transactional
    public ReviewConsensusResponse recalculateForLeader(UUID currentUserId, UUID groupId, UUID taskId) {
        requireTaskLeader(currentUserId, groupId, taskId);
        return recalculate(taskId, currentUserId, currentUserId, reportMapper.selectSubmittedActiveAssignmentReportsByTaskId(taskId));
    }

    @Override
    @Transactional
    public ReviewConsensusResponse updateForLeader(UUID currentUserId, UUID groupId, UUID taskId, ReviewConsensusUpdateRequest request) {
        requireTaskLeader(currentUserId, groupId, taskId);
        return update(taskId, currentUserId, request);
    }

    @Override
    @Transactional
    public ReviewConsensusResponse confirmForLeader(UUID currentUserId, UUID groupId, UUID taskId) {
        requireTaskLeader(currentUserId, groupId, taskId);
        return confirm(taskId, currentUserId);
    }

    @Override
    public boolean canAccessConsensus(UUID currentUserId, boolean admin, UUID taskId) {
        if (admin) {
            return true;
        }
        if (currentUserId == null) {
            return false;
        }
        ReviewTaskEntity task = taskMapper.selectById(taskId);
        if (task != null && task.getGroupId() != null) {
            ReviewGroupEntity group = groupMapper.selectById(task.getGroupId());
            return group != null
                    && GROUP_STATUS_ACTIVE.equals(group.getStatus())
                    && currentUserId.equals(group.getLeaderUserId());
        }
        ReviewAssignmentEntity lead = assignmentMapper.selectLeadByTaskId(taskId);
        return lead != null && currentUserId.equals(lead.getReviewerUserId());
    }

    private ReviewConsensusResponse recalculate(UUID taskId, UUID operatorUserId, UUID leadReviewerUserId, List<ReviewReportEntity> reports) {
        ReviewConsensusEntity consensus = consensusMapper.selectByTaskId(taskId);
        if (isConfirmed(consensus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "最终共识已确认，不能重新计算");
        }

        if (reports == null || reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有已提交的个人评审报告");
        }
        requireAllAssignmentsSubmitted(taskId);

        Map<String, Object> beforeSnapshot = consensus == null ? Map.of("status", "MISSING") : consensusSnapshot(consensus);
        ConsensusCalculator.Result result = consensusCalculator.calculate(reports);
        boolean creating = consensus == null;
        OffsetDateTime now = OffsetDateTime.now();
        if (creating) {
            consensus = new ReviewConsensusEntity();
            consensus.setId(UUID.randomUUID());
            consensus.setTaskId(taskId);
            consensus.setCreatedAt(now);
        }
        consensus.setLeadReviewerUserId(leadReviewerUserId);
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
        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("scope", operatorUserId == null ? "legacy" : "review-consensus");
        clientInfo.put("reportCount", reports.size());
        clientInfo.put("creating", creating);
        reviewAuditService.append(
                taskId,
                operatorUserId,
                "RECALCULATE_CONSENSUS",
                "重新计算最终评分与共识草稿",
                beforeSnapshot,
                consensusSnapshot(consensus),
                clientInfo
        );
        return toResponse(consensus, reports);
    }

    private ReviewTaskEntity requireTaskLeader(UUID currentUserId, UUID groupId, UUID taskId) {
        ReviewTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        if (task.getGroupId() == null || !task.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能处理本组评审任务");
        }
        ReviewGroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审小组不存在");
        }
        if (!GROUP_STATUS_ACTIVE.equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审小组不可用");
        }
        if (currentUserId == null || !currentUserId.equals(group.getLeaderUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能处理自己负责小组的最终评分");
        }
        return task;
    }

    private UUID resolveConsensusLeadUserId(UUID taskId) {
        ReviewTaskEntity task = taskMapper.selectById(taskId);
        if (task != null && task.getLeaderUserId() != null) {
            return task.getLeaderUserId();
        }
        if (task != null && task.getGroupId() != null) {
            ReviewGroupEntity group = groupMapper.selectById(task.getGroupId());
            if (group != null) {
                return group.getLeaderUserId();
            }
        }
        ReviewAssignmentEntity lead = assignmentMapper.selectLeadByTaskId(taskId);
        return lead == null ? null : lead.getReviewerUserId();
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

    private Map<String, Object> consensusSnapshot(ReviewConsensusEntity consensus) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", consensus.getId());
        snapshot.put("taskId", consensus.getTaskId());
        snapshot.put("leadReviewerUserId", consensus.getLeadReviewerUserId());
        snapshot.put("status", consensus.getStatus());
        snapshot.put("finalScore", consensus.getFinalScore());
        snapshot.put("finalRecommendation", consensus.getFinalRecommendation());
        snapshot.put("confirmedByUserId", consensus.getConfirmedByUserId());
        snapshot.put("confirmedAt", consensus.getConfirmedAt());
        return snapshot;
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
