package com.lqr.paperragserver.auth.service;

import java.util.List;
import java.util.UUID;

/**
 * 管理员用户管理服务，封装用户分页、创建、状态和角色维护能力。
 */
public interface UserAdminService {

    PageResult listUsers(int page, int size, String keyword, String status);

    UserView createUser(CreateUserCommand command);

    UserView updateUser(UUID id, UpdateUserCommand command);

    UserView updateRoles(UUID id, List<String> roles);

    UserView updateStatus(UUID id, String status);

    void resetPassword(UUID id, String password);

    void deleteUser(UUID id);

    /**
     * 用户分页查询结果。
     */
    record PageResult(List<UserView> items, int page, int size, long total) {
    }

    /**
     * 管理后台展示的用户信息。
     */
    record UserView(
            String id,
            String username,
            String displayName,
            String email,
            String status,
            List<String> roles,
            String lastLoginAt,
            String createdAt,
            String updatedAt
    ) {
    }

    /**
     * 创建用户命令。
     */
    record CreateUserCommand(String username, String password, String displayName, String email, List<String> roles) {
    }

    /**
     * 更新用户基础资料命令。
     */
    record UpdateUserCommand(String displayName, String email) {
    }
}