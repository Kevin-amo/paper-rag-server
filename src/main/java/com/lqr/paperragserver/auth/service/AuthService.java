package com.lqr.paperragserver.auth.service;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;

/**
 * 认证服务，负责登录校验、注册、访问令牌生成和当前用户信息转换。
 */
public interface AuthService {

    /**
     * 用户登录校验，成功后签发访问令牌。
     *
     * @param username 用户名
     * @param password 密码
     * @param clientIp 客户端IP地址
     * @return 登录结果，包含令牌和用户信息
     */
    LoginResult login(String username, String password, String clientIp);

    /**
     * 发送邮箱注册验证码。
     *
     * @param email 邮箱地址
     * @param clientIp 客户端IP地址
     */
    void createRegisterEmailCode(String email, String clientIp);

    /**
     * 使用邮箱验证码完成注册并自动登录。
     *
     * @param username 用户名
     * @param password 密码
     * @param email 邮箱地址
     * @param emailCode 邮箱验证码
     * @return 登录结果，包含令牌和用户信息
     */
    LoginResult registerWithEmailCode(String username, String password, String email, String emailCode);

    /**
     * 用户登出，撤销当前访问令牌。
     *
     * @param token 访问令牌
     */
    void logout(String token);

    /**
     * 获取当前登录用户信息。
     *
     * @param principal 当前用户主体
     * @return 当前用户信息
     */
    CurrentUser currentUser(SecurityUserPrincipal principal);

    /**
     * 修改当前用户密码，成功后撤销该用户所有已签发令牌。
     *
     * @param principal 当前用户主体
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     * @return 更新后的用户信息
     */
    CurrentUser changePassword(SecurityUserPrincipal principal, String currentPassword, String newPassword);

    /**
     * 修改当前用户昵称。
     *
     * @param principal 当前用户主体
     * @param displayName 新昵称
     * @return 更新后的用户信息
     */
    CurrentUser changeDisplayName(SecurityUserPrincipal principal, String displayName);

    /**
     * 发送换绑邮箱验证码。
     *
     * @param principal 当前用户主体
     * @param email 新邮箱地址
     * @param clientIp 客户端IP地址
     */
    void createChangeEmailCode(SecurityUserPrincipal principal, String email, String clientIp);

    /**
     * 使用验证码换绑邮箱。
     *
     * @param principal 当前用户主体
     * @param email 新邮箱地址
     * @param emailCode 邮箱验证码
     * @return 更新后的用户信息
     */
    CurrentUser changeEmail(SecurityUserPrincipal principal, String email, String emailCode);

    /**
     * 登录成功响应，包含访问令牌和当前用户摘要。
     */
    record LoginResult(
            String accessToken,
            String tokenType,
            long expiresIn,
            CurrentUser user
    ) {
    }

    /**
     * 当前登录用户信息。
     */
    record CurrentUser(
            String id,
            String username,
            String displayName,
            String email,
            String avatarUrl,
            java.util.List<String> roles
    ) {
    }
}