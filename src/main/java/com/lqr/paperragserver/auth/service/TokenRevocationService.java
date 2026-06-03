package com.lqr.paperragserver.auth.service;

import java.time.Instant;
import java.util.UUID;

/**
 * 访问令牌撤销服务，用于服务端失效已签发但尚未过期的 JWT。
 */
public interface TokenRevocationService {

    /**
     * 撤销指定令牌，使其在过期前无法使用。
     *
     * @param token 令牌字符串
     * @param expiresAt 令牌过期时间
     */
    void revoke(String token, Instant expiresAt);

    /**
     * 判断令牌是否已被撤销。
     *
     * @param token 令牌字符串
     * @return 已撤销返回 true，否则返回 false
     */
    boolean isRevoked(String token);

    /**
     * 撤销指定用户的所有令牌，通过记录密码变更时间实现。
     *
     * @param userId 用户ID
     */
    void revokeAllTokensForUser(UUID userId);

    /**
     * 判断令牌是否因密码变更而失效，即令牌签发时间早于密码变更时间。
     *
     * @param userId 用户ID
     * @param issuedAt 令牌签发时间
     * @return 令牌因密码变更而失效返回 true，否则返回 false
     */
    boolean isTokenRevokedByPasswordChange(UUID userId, Instant issuedAt);
}