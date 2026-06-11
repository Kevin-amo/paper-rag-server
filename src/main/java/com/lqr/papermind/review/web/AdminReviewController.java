package com.lqr.papermind.review.web;

import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskDetailResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.service.AdminReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * 管理员评审控制器
 * <p>提供管理员专用的评审任务管理、分配和共识管理API接口</p>
 */
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    /**
     * 分页查询评审任务列表（管理员视角）
     *
     * @param principal 当前认证用户
     * @param keyword   搜索关键词（可选）
     * @param status    任务状态筛选（可选）
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 分页后的评审任务摘要列表
     */
    @GetMapping("/tasks")
    public PageResponse<AdminReviewTaskSummaryResponse> listTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                                  @RequestParam(value = "status", required = false) String status,
                                                                  @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                                  @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        requireAdmin(principal);
        return adminReviewService.listTasks(keyword, status, page, size);
    }

    /**
     * 获取评审任务详情（管理员视角）
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 评审任务详细信息
     */
    @GetMapping("/tasks/{taskId}")
    public AdminReviewTaskDetailResponse getTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable UUID taskId) {
        requireAdmin(principal);
        return adminReviewService.getTask(taskId);
    }

    /**
     * 分配评审员
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @param request   评审员分配请求
     * @return 评审分配结果列表
     */
    @PostMapping("/tasks/{taskId}/assignments")
    public List<ReviewAssignmentResponse> assignReviewers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                          @PathVariable UUID taskId,
                                                          @Valid @RequestBody ReviewAssignmentRequest request) {
        requireAdmin(principal);
        return adminReviewService.assignReviewers(taskId, principal.getId(), request);
    }

    /**
     * 重新计算评审共识
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 重新计算后的评审共识
     */
    @PostMapping("/tasks/{taskId}/consensus/recalculate")
    public ReviewConsensusResponse recalculateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                         @PathVariable UUID taskId) {
        requireAdmin(principal);
        return adminReviewService.recalculateConsensus(taskId, principal.getId());
    }

    /**
     * 更新评审共识
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @param request   共识更新请求
     * @return 更新后的评审共识
     */
    @PatchMapping("/tasks/{taskId}/consensus")
    public ReviewConsensusResponse updateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                    @PathVariable UUID taskId,
                                                    @Valid @RequestBody ReviewConsensusUpdateRequest request) {
        requireAdmin(principal);
        return adminReviewService.updateConsensus(taskId, principal.getId(), request);
    }

    /**
     * 确认评审共识
     *
     * @param principal 当前认证用户
     * @param taskId    评审任务ID
     * @return 确认后的评审共识
     */
    @PostMapping("/tasks/{taskId}/consensus/confirm")
    public ReviewConsensusResponse confirmConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                     @PathVariable UUID taskId) {
        requireAdmin(principal);
        return adminReviewService.confirmConsensus(taskId, principal.getId());
    }

    /**
     * 获取评审员工作负载列表
     *
     * @param principal 当前认证用户
     * @return 评审员工作负载列表
     */
    @GetMapping("/reviewer-loads")
    public List<ReviewerLoadResponse> listReviewerLoads(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        requireAdmin(principal);
        return adminReviewService.listReviewerLoads();
    }

    /**
     * 验证用户是否具有管理员权限
     *
     * @param principal 当前认证用户
     * @throws ResponseStatusException 权限不足时抛出403异常
     */
    private void requireAdmin(SecurityUserPrincipal principal) {
        if (principal == null || !principal.getRoles().contains(RoleCodes.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }
}
