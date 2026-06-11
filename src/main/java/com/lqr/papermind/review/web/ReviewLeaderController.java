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

/**
 * 评审组长控制器
 * <p>提供评审组长专用的评审组管理、任务分配和共识管理API接口</p>
 */
@RestController
@RequestMapping("/review-leader")
@RequiredArgsConstructor
public class ReviewLeaderController {

    private final ReviewGroupService reviewGroupService;
    private final ReviewAssignmentService reviewAssignmentService;
    private final ReviewConsensusService reviewConsensusService;

    /**
     * 获取当前用户负责的评审组列表
     *
     * @param principal 当前认证用户
     * @return 评审组列表
     */
    @GetMapping("/groups")
    public List<ReviewGroupResponse> listMyGroups(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        requireReviewer(principal);
        return reviewGroupService.listLeaderGroups(principal.getId());
    }

    /**
     * 获取评审组成员列表
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @return 评审组成员列表
     */
    @GetMapping("/groups/{groupId}/members")
    public List<ReviewGroupMemberResponse> listMyGroupMembers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                              @PathVariable UUID groupId) {
        requireReviewer(principal);
        return reviewGroupService.listGroupMembersForLeader(principal.getId(), groupId);
    }

    /**
     * 获取评审组任务列表
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @return 评审任务摘要列表
     */
    @GetMapping("/groups/{groupId}/tasks")
    public List<AdminReviewTaskSummaryResponse> listGroupTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                              @PathVariable UUID groupId) {
        requireReviewer(principal);
        return reviewGroupService.listGroupTasksForLeader(principal.getId(), groupId);
    }

    /**
     * 获取评审组未分配的任务列表
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @return 未分配的评审任务列表
     */
    @GetMapping("/groups/{groupId}/tasks/unassigned")
    public List<AdminReviewTaskSummaryResponse> listUnassignedTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                    @PathVariable UUID groupId) {
        requireReviewer(principal);
        return reviewGroupService.listUnassignedTasksForLeader(principal.getId(), groupId);
    }

    /**
     * 分配评审任务给评审员
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param taskId    评审任务ID
     * @param request   评审员分配请求
     * @return 评审分配结果列表
     */
    @PostMapping("/groups/{groupId}/tasks/{taskId}/assignments")
    public List<ReviewAssignmentResponse> assignTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                     @PathVariable UUID groupId,
                                                     @PathVariable UUID taskId,
                                                     @Valid @RequestBody LeaderReviewAssignmentRequest request) {
        requireReviewer(principal);
        return reviewAssignmentService.assignReviewersByLeader(principal.getId(), groupId, taskId, request);
    }

    /**
     * 获取评审任务报告列表
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param taskId    评审任务ID
     * @return 评审报告列表
     */
    @GetMapping("/groups/{groupId}/tasks/{taskId}/reports")
    public List<ReviewReportResponse> listTaskReports(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @PathVariable UUID groupId,
                                                      @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.listReportsForLeader(principal.getId(), groupId, taskId);
    }

    /**
     * 获取评审任务共识
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param taskId    评审任务ID
     * @return 评审共识信息
     */
    @GetMapping("/groups/{groupId}/tasks/{taskId}/consensus")
    public ReviewConsensusResponse getConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable UUID groupId,
                                                 @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.getForTaskForLeader(principal.getId(), groupId, taskId);
    }

    /**
     * 重新计算评审共识
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param taskId    评审任务ID
     * @return 重新计算后的评审共识
     */
    @PostMapping("/groups/{groupId}/tasks/{taskId}/consensus/recalculate")
    public ReviewConsensusResponse recalculateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                        @PathVariable UUID groupId,
                                                        @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.recalculateForLeader(principal.getId(), groupId, taskId);
    }

    /**
     * 更新评审共识
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param taskId    评审任务ID
     * @param request   共识更新请求
     * @return 更新后的评审共识
     */
    @PatchMapping("/groups/{groupId}/tasks/{taskId}/consensus")
    public ReviewConsensusResponse updateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @PathVariable UUID groupId,
                                                   @PathVariable UUID taskId,
                                                   @Valid @RequestBody ReviewConsensusUpdateRequest request) {
        requireReviewer(principal);
        return reviewConsensusService.updateForLeader(principal.getId(), groupId, taskId, request);
    }

    /**
     * 确认评审共识
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param taskId    评审任务ID
     * @return 确认后的评审共识
     */
    @PostMapping("/groups/{groupId}/tasks/{taskId}/consensus/confirm")
    public ReviewConsensusResponse confirmConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                    @PathVariable UUID groupId,
                                                    @PathVariable UUID taskId) {
        requireReviewer(principal);
        return reviewConsensusService.confirmForLeader(principal.getId(), groupId, taskId);
    }

    /**
     * 验证用户是否具有评审员或管理员权限
     *
     * @param principal 当前认证用户
     * @throws ResponseStatusException 权限不足时抛出403异常
     */
    private void requireReviewer(SecurityUserPrincipal principal) {
        if (principal == null || (!principal.getRoles().contains(RoleCodes.REVIEWER) && !principal.getRoles().contains(RoleCodes.ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要评审员权限");
        }
    }
}
