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

/**
 * 管理员评审配置控制器
 * <p>提供评审批次、评审组和评审组成员的管理API接口</p>
 */
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewConfigController {

    private final ReviewGroupService reviewGroupService;

    /**
     * 分页查询评审批次列表
     *
     * @param principal 当前认证用户
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 分页后的评审批次列表
     */
    @GetMapping("/batches")
    public PageResponse<ReviewBatchResponse> listBatches(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                         @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        requireAdmin(principal);
        return reviewGroupService.listBatches(page, size);
    }

    /**
     * 创建评审批次
     *
     * @param principal 当前认证用户
     * @param request   评审批次创建请求
     * @return 创建的评审批次
     */
    @PostMapping("/batches")
    public ReviewBatchResponse createBatch(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @Valid @RequestBody ReviewBatchRequest request) {
        requireAdmin(principal);
        return reviewGroupService.createBatch(principal.getId(), request);
    }

    /**
     * 更新评审批次
     *
     * @param principal 当前认证用户
     * @param batchId   评审批次ID
     * @param request   评审批次更新请求
     * @return 更新后的评审批次
     */
    @PatchMapping("/batches/{batchId}")
    public ReviewBatchResponse updateBatch(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @PathVariable UUID batchId,
                                           @Valid @RequestBody ReviewBatchRequest request) {
        requireAdmin(principal);
        return reviewGroupService.updateBatch(batchId, request);
    }

    /**
     * 获取评审组列表
     *
     * @param principal 当前认证用户
     * @param batchId   评审批次ID（可选）
     * @return 评审组列表
     */
    @GetMapping("/groups")
    public List<ReviewGroupResponse> listGroups(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                @RequestParam(value = "batchId", required = false) UUID batchId) {
        requireAdmin(principal);
        return reviewGroupService.listGroups(batchId);
    }

    /**
     * 创建评审组
     *
     * @param principal 当前认证用户
     * @param request   评审组创建请求
     * @return 创建的评审组
     */
    @PostMapping("/groups")
    public ReviewGroupResponse createGroup(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @Valid @RequestBody ReviewGroupRequest request) {
        requireAdmin(principal);
        return reviewGroupService.createGroup(principal.getId(), request);
    }

    /**
     * 更新评审组
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param request   评审组更新请求
     * @return 更新后的评审组
     */
    @PatchMapping("/groups/{groupId}")
    public ReviewGroupResponse updateGroup(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                           @PathVariable UUID groupId,
                                           @Valid @RequestBody ReviewGroupRequest request) {
        requireAdmin(principal);
        return reviewGroupService.updateGroup(groupId, request);
    }

    /**
     * 获取评审组成员列表
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @return 评审组成员列表
     */
    @GetMapping("/groups/{groupId}/members")
    public List<ReviewGroupMemberResponse> listGroupMembers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                            @PathVariable UUID groupId) {
        requireAdmin(principal);
        return reviewGroupService.listGroupMembers(groupId);
    }

    /**
     * 替换评审组成员
     *
     * @param principal 当前认证用户
     * @param groupId   评审组ID
     * @param request   评审组成员更新请求
     * @return 更新后的评审组成员列表
     */
    @PutMapping("/groups/{groupId}/members")
    public List<ReviewGroupMemberResponse> replaceGroupMembers(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                               @PathVariable UUID groupId,
                                                               @Valid @RequestBody ReviewGroupMemberUpdateRequest request) {
        requireAdmin(principal);
        return reviewGroupService.replaceGroupMembers(principal.getId(), groupId, request);
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
