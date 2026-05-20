package com.lqr.paperragserver.auth.service;

import java.time.Instant;

/**
 * 访问令牌撤销服务，用于服务端失效已签发但尚未过期的 JWT。
 */
public interface TokenRevocationService {

    void revoke(String token, Instant expiresAt);

    boolean isRevoked(String token);
}