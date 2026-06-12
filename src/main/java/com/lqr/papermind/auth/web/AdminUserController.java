package com.lqr.papermind.auth.web;

import com.lqr.papermind.auth.dto.CreateUserRequest;
import com.lqr.papermind.auth.dto.ResetPasswordRequest;
import com.lqr.papermind.auth.dto.UpdateRolesRequest;
import com.lqr.papermind.auth.dto.UpdateStatusRequest;
import com.lqr.papermind.auth.dto.UpdateUserRequest;
import com.lqr.papermind.auth.service.UserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 管理员用户管理接口，提供用户分页、创建、角色调整、状态调整和密码重置能力。
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;

    /**
     * 分页查询用户列表接口，支持关键字和状态筛选。
     *
     * @param page 页码（从0开始），默认0
     * @param size 每页大小，默认20，最大100
     * @param keyword 搜索关键字，匹配用户名、昵称或邮箱
     * @param status 用户状态筛选
     * @return 分页查询结果
     */
    @GetMapping
    public UserAdminService.PageResult listUsers(@RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "status", required = false) String status) {
        return userAdminService.listUsers(page, size, keyword, status);
    }

    /**
     * 创建新用户接口。
     *
     * @param request 创建用户请求体，包含用户名、密码、昵称、邮箱和角色
     * @return 创建后的用户信息
     */
    @PostMapping
    public UserAdminService.UserView createUser(@Valid @RequestBody CreateUserRequest request) {
        return userAdminService.createUser(new UserAdminService.CreateUserCommand(
                request.username(),
                request.password(),
                request.displayName(),
                request.email(),
                request.roles()
        ));
    }

    /**
     * 更新用户基础资料接口。
     *
     * @param id 用户ID
     * @param request 更新用户请求体，包含昵称和邮箱
     * @return 更新后的用户信息
     */
    @PatchMapping("/{id}")
    public UserAdminService.UserView updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.updateUser(id, new UserAdminService.UpdateUserCommand(request.displayName(), request.email()));
    }

    /**
     * 更新用户角色列表接口。
     *
     * @param id 用户ID
     * @param request 更新角色请求体，包含新的角色编码列表
     * @return 更新后的用户信息
     */
    @PatchMapping("/{id}/roles")
    public UserAdminService.UserView updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return userAdminService.updateRoles(id, request.roles());
    }

    /**
     * 更新用户状态接口。
     *
     * @param id 用户ID
     * @param request 更新状态请求体，包含新状态（ACTIVE 或 DISABLED）
     * @return 更新后的用户信息
     */
    @PatchMapping("/{id}/status")
    public UserAdminService.UserView updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return userAdminService.updateStatus(id, request.status());
    }

    /**
     * 重置用户密码接口。
     *
     * @param id 用户ID
     * @param request 重置密码请求体，包含新密码
     */
    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.password());
    }

    /**
     * 删除用户接口。
     *
     * @param id 用户ID
     */
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable UUID id) {
        userAdminService.deleteUser(id);
    }
}