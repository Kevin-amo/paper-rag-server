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

@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewService adminReviewService;

    @GetMapping("/tasks")
    public PageResponse<AdminReviewTaskSummaryResponse> listTasks(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                                  @RequestParam(value = "status", required = false) String status,
                                                                  @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                                  @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        requireAdmin(principal);
        return adminReviewService.listTasks(keyword, status, page, size);
    }

    @GetMapping("/tasks/{taskId}")
    public AdminReviewTaskDetailResponse getTask(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                 @PathVariable UUID taskId) {
        requireAdmin(principal);
        return adminReviewService.getTask(taskId);
    }

    @PostMapping("/tasks/{taskId}/assignments")
    public List<ReviewAssignmentResponse> assignReviewers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                          @PathVariable UUID taskId,
                                                          @Valid @RequestBody ReviewAssignmentRequest request) {
        requireAdmin(principal);
        return adminReviewService.assignReviewers(taskId, principal.getId(), request);
    }

    @PostMapping("/tasks/{taskId}/consensus/recalculate")
    public ReviewConsensusResponse recalculateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                         @PathVariable UUID taskId) {
        requireAdmin(principal);
        return adminReviewService.recalculateConsensus(taskId, principal.getId());
    }

    @PatchMapping("/tasks/{taskId}/consensus")
    public ReviewConsensusResponse updateConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                    @PathVariable UUID taskId,
                                                    @Valid @RequestBody ReviewConsensusUpdateRequest request) {
        requireAdmin(principal);
        return adminReviewService.updateConsensus(taskId, principal.getId(), request);
    }

    @PostMapping("/tasks/{taskId}/consensus/confirm")
    public ReviewConsensusResponse confirmConsensus(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                     @PathVariable UUID taskId) {
        requireAdmin(principal);
        return adminReviewService.confirmConsensus(taskId, principal.getId());
    }

    @GetMapping("/reviewer-loads")
    public List<ReviewerLoadResponse> listReviewerLoads(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        requireAdmin(principal);
        return adminReviewService.listReviewerLoads();
    }

    private void requireAdmin(SecurityUserPrincipal principal) {
        if (principal == null || !principal.getRoles().contains(RoleCodes.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }
}
