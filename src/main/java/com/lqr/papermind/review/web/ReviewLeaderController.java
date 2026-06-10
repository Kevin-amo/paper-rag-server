package com.lqr.papermind.review.web;

import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.LeaderReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewGroupMemberResponse;
import com.lqr.papermind.review.dto.ReviewGroupResponse;
import com.lqr.papermind.review.dto.ReviewReportResponse;
import com.lqr.papermind.review.service.ReviewAssignmentService;
import com.lqr.papermind.review.service.ReviewConsensusService;
import com.lqr.papermind.review.service.ReviewGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/review-leader")
@RequiredArgsConstructor
public class ReviewLeaderController {

    private final ReviewGroupService reviewGroupService;
    private final ReviewAssignmentService reviewAssignmentService;
    private final ReviewConsensusService reviewConsensusService;

    @GetMapping("/groups")
    public List<ReviewGroupResponse> listMyGroups(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        requireReviewer(principal);
        return reviewGroupService.listLeaderGroups(principal.getId());
    }

    @GetMapping("/groups/{groupId}/members")
    public List<ReviewGroupMemberResponse> listMyGroupMembers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                              @PathVariable UUID groupId) {
        requireReviewer(principal);
        return reviewGroupService.listGroupMembersForLeader(principal.getId(), groupId);
    }

    @GetMapping("/groups/{groupId}/tasks")
    public List<AdminReviewTaskSummaryResponse> listGroupTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                              @PathVariable UUID groupId) {
        requireReviewer(principal);
        return reviewGroupService.listGroupTasksForLeader(principal.getId(), groupId);
    }

    @GetMapping("/groups/{groupId}/tasks/unassigned")
    public List<AdminReviewTaskSummaryResponse> listUnassignedTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                    @PathVariable UUID groupId) {
        requireReviewer(principal);
        return reviewGroupService.listUnassignedTasksForLeader(principal.getId(), groupId);
    }

    @PostMapping("/groups/{groupId}/tasks/{taskId}/assignments")
    public List<ReviewAssignmentResponse> assignTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                     @PathVariable UUID groupId,
                                                     @PathVariable UUID taskId,
                                                     @Valid @RequestBody LeaderReviewAssignmentRequest request) {
        requireReviewer(principal);
        return reviewAssignmentService.assignReviewersByLeader(principal.getId(), groupId, taskId, request);
    }

    @GetMapping("/groups/{groupId}/tasks/{taskId}/reports")
    public List<ReviewReportResponse> listTaskReports(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @PathVariable UUID groupId,
                                                      @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.listReportsForLeader(principal.getId(), groupId, taskId);
    }

    @GetMapping("/groups/{groupId}/tasks/{taskId}/consensus")
    public ReviewConsensusResponse getConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable UUID groupId,
                                                 @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.getForTaskForLeader(principal.getId(), groupId, taskId);
    }

    @PostMapping("/groups/{groupId}/tasks/{taskId}/consensus/recalculate")
    public ReviewConsensusResponse recalculateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                        @PathVariable UUID groupId,
                                                        @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.recalculateForLeader(principal.getId(), groupId, taskId);
    }

    @PatchMapping("/groups/{groupId}/tasks/{taskId}/consensus")
    public ReviewConsensusResponse updateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @PathVariable UUID groupId,
                                                   @PathVariable UUID taskId,
                                                   @Valid @RequestBody ReviewConsensusUpdateRequest request) {
        requireReviewer(principal);
        return reviewConsensusService.updateForLeader(principal.getId(), groupId, taskId, request);
    }

    @PostMapping("/groups/{groupId}/tasks/{taskId}/consensus/confirm")
    public ReviewConsensusResponse confirmConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                    @PathVariable UUID groupId,
                                                    @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.confirmForLeader(principal.getId(), groupId, taskId);
    }

    private void requireReviewer(SecurityUserPrincipal principal) {
        if (principal == null || (!principal.getRoles().contains(RoleCodes.REVIEWER) && !principal.getRoles().contains(RoleCodes.ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要评审员权限");
        }
    }
}