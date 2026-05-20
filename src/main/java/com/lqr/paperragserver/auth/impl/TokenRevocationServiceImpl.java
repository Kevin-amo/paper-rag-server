package com.lqr.paperragserver.auth.impl;

import com.lqr.paperragserver.auth.service.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 基于 Redis 的 JWT 撤销表，按 token 哈希记录并跟随原 token 过期自动清理。
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationServiceImpl implements TokenRevocationService {

    private static final String REVOKED_TOKEN_KEY_PREFIX = "auth:jwt:revoked:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void revoke(String token, Instant expiresAt) {
        if (token == null || token.isBlank() || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(revokedTokenKey(token), "1", ttl);
    }

    @Override
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(revokedTokenKey(token)));
    }

    private String revokedTokenKey(String token) {
        return REVOKED_TOKEN_KEY_PREFIX + sha256(token.trim());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}