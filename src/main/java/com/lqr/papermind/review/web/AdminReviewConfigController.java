package com.lqr.papermind.review.web;

import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.dto.ReviewBatchRequest;
import com.lqr.papermind.review.dto.ReviewBatchResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberUpdateRequest;
import com.lqr.papermind.review.dto.ReviewGroupRequest;
import com.lqr.papermind.review.dto.ReviewGroupResponse;
import com.lqr.papermind.review.service.ReviewGroupService;
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
import org.springframework.web.bind.annotation.PutMapping;
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
public class AdminReviewConfigController {

    private final ReviewGroupService reviewGroupService;

    @GetMapping("/batches")
    public PageResponse<ReviewBatchResponse> listBatches(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                         @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        requireAdmin(principal);
        return reviewGroupService.listBatches(page, size);
    }

    @PostMapping("/batches")
    public ReviewBatchResponse createBatch(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @Valid @RequestBody ReviewBatchRequest request) {
        requireAdmin(principal);
        return reviewGroupService.createBatch(principal.getId(), request);
    }

    @PatchMapping("/batches/{batchId}")
    public ReviewBatchResponse updateBatch(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @PathVariable UUID batchId,
                                           @Valid @RequestBody ReviewBatchRequest request) {
        requireAdmin(principal);
        return reviewGroupService.updateBatch(batchId, request);
    }

    @GetMapping("/groups")
    public List<ReviewGroupResponse> listGroups(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                @RequestParam(value = "batchId", required = false) UUID batchId) {
        requireAdmin(principal);
        return reviewGroupService.listGroups(batchId);
    }

    @PostMapping("/groups")
    public ReviewGroupResponse createGroup(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @Valid @RequestBody ReviewGroupRequest request) {
        requireAdmin(principal);
        return reviewGroupService.createGroup(principal.getId(), request);
    }

    @PatchMapping("/groups/{groupId}")
    public ReviewGroupResponse updateGroup(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @PathVariable UUID groupId,
                                           @Valid @RequestBody ReviewGroupRequest request) {
        requireAdmin(principal);
        return reviewGroupService.updateGroup(groupId, request);
    }

    @GetMapping("/groups/{groupId}/members")
    public List<ReviewGroupMemberResponse> listGroupMembers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                            @PathVariable UUID groupId) {
        requireAdmin(principal);
        return reviewGroupService.listGroupMembers(groupId);
    }

    @PutMapping("/groups/{groupId}/members")
    public List<ReviewGroupMemberResponse> replaceGroupMembers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                               @PathVariable UUID groupId,
                                                               @Valid @RequestBody ReviewGroupMemberUpdateRequest request) {
        requireAdmin(principal);
        return reviewGroupService.replaceGroupMembers(principal.getId(), groupId, request);
    }

    private void requireAdmin(SecurityUserPrincipal principal) {
        if (principal == null || !principal.getRoles().contains(RoleCodes.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }
}