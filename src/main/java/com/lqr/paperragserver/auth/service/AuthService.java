package com.lqr.paperragserver.auth.service;

import com.lqr.paperragserver.auth.security.SecurityUserPrincipal;

/**
 * 认证服务，负责登录校验、注册、访问令牌生成和当前用户信息转换。
 */
public interface AuthService {

    LoginResult login(String username, String password, String clientIp);

    void createRegisterEmailCode(String email, String clientIp);

    LoginResult registerWithEmailCode(String username, String password, String email, String emailCode);

    void logout(String token);

    CurrentUser currentUser(SecurityUserPrincipal principal);

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