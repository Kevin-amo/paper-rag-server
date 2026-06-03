package com.lqr.paperragserver.auth.service;

import java.util.List;
import java.util.UUID;

/**
 * 管理员用户管理服务，封装用户分页、创建、状态和角色维护能力。
 */
public interface UserAdminService {

    /**
     * 分页查询用户列表，支持关键字和状态筛选。
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param keyword 搜索关键字，匹配用户名、昵称或邮箱
     * @param status 用户状态筛选
     * @return 分页查询结果
     */
    PageResult listUsers(int page, int size, String keyword, String status);

    /**
     * 创建新用户。
     *
     * @param command 创建用户命令
     * @return 创建后的用户信息
     */
    UserView createUser(CreateUserCommand command);

    /**
     * 更新用户基础资料（昵称和邮箱）。
     *
     * @param id 用户ID
     * @param command 更新用户命令
     * @return 更新后的用户信息
     */
    UserView updateUser(UUID id, UpdateUserCommand command);

    /**
     * 更新用户角色列表。
     *
     * @param id 用户ID
     * @param roles 新的角色编码列表
     * @return 更新后的用户信息
     */
    UserView updateRoles(UUID id, List<String> roles);

    /**
     * 更新用户状态。
     *
     * @param id 用户ID
     * @param status 新状态（ACTIVE 或 DISABLED）
     * @return 更新后的用户信息
     */
    UserView updateStatus(UUID id, String status);

    /**
     * 重置用户密码，同时撤销该用户所有已签发令牌。
     *
     * @param id 用户ID
     * @param password 新密码
     */
    void resetPassword(UUID id, String password);

    /**
     * 删除用户及其角色关联。
     *
     * @param id 用户ID
     */
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
            String avatarUrl,
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