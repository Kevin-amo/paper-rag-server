package com.lqr.paperragserver.web;

import com.lqr.paperragserver.auth.service.UserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

import java.util.List;
import java.util.UUID;

/**
 * 管理员用户管理接口，提供用户分页、创建、角色调整、状态调整和密码重置能力。
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    public UserAdminService.PageResult listUsers(@RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "status", required = false) String status) {
        return userAdminService.listUsers(page, size, keyword, status);
    }

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

    @PatchMapping("/{id}")
    public UserAdminService.UserView updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.updateUser(id, new UserAdminService.UpdateUserCommand(request.displayName(), request.email()));
    }

    @PatchMapping("/{id}/roles")
    public UserAdminService.UserView updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return userAdminService.updateRoles(id, request.roles());
    }

    @PatchMapping("/{id}/status")
    public UserAdminService.UserView updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return userAdminService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.password());
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable UUID id) {
        userAdminService.deleteUser(id);
    }

    /**
     * 创建用户请求体。
     */
    public record CreateUserRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            String displayName,
            String email,
            List<String> roles
    ) {
    }

    /**
     * 更新用户基础资料请求体。
     */
    public record UpdateUserRequest(String displayName, String email) {
    }

    /**
     * 更新用户角色请求体。
     */
    public record UpdateRolesRequest(List<String> roles) {
    }

    /**
     * 更新用户状态请求体。
     */
    public record UpdateStatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    /**
     * 重置密码请求体。
     */
    public record ResetPasswordRequest(@NotBlank(message = "密码不能为空") String password) {
    }
}