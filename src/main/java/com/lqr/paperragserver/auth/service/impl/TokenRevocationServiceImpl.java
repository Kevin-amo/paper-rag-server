package com.lqr.paperragserver.auth.service.impl;

import com.lqr.paperragserver.auth.config.SecurityProperties;
import com.lqr.paperragserver.auth.service.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 基于 Redis 的访问令牌撤销服务，通过缓存撤销记录和密码变更时间实现令牌失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationServiceImpl implements TokenRevocationService {

    private static final String REVOKED_TOKEN_KEY_PREFIX = "auth:jwt:revoked:";
    private static final String PWD_CHANGED_AT_KEY_PREFIX = "auth:user:pwdChangedAt:";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    /**
     * 撤销指定令牌，将其存入 Redis 直到过期。
     *
     * @param token 令牌字符串
     * @param expiresAt 令牌过期时间
     */
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

    /**
     * 判断令牌是否已被撤销。
     *
     * @param token 令牌字符串
     * @return 已撤销返回 true，否则返回 false
     */
    @Override
    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(revokedTokenKey(token)));
    }

    /**
     * 撤销指定用户的所有令牌，通过记录密码变更时间戳实现。
     *
     * @param userId 用户ID
     */
    @Override
    public void revokeAllTokensForUser(UUID userId) {
        Instant now = Instant.now();
        String epochMillis = String.valueOf(now.toEpochMilli());
        Duration ttl = securityProperties.jwt().accessTokenTtl().multipliedBy(2);
        String key = pwdChangedAtKey(userId);
        redisTemplate.opsForValue().set(key, epochMillis, ttl);
        log.info("Token revocation triggered for userId={}, pwdChangedAt={}", userId, now);
    }

    /**
     * 判断令牌是否因密码变更而失效，即令牌签发时间早于密码变更时间。
     *
     * @param userId 用户ID
     * @param issuedAt 令牌签发时间
     * @return 令牌因密码变更而失效返回 true，否则返回 false
     */
    @Override
    public boolean isTokenRevokedByPasswordChange(UUID userId, Instant issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        String key = pwdChangedAtKey(userId);
        String storedEpochMillis = redisTemplate.opsForValue().get(key);
        if (storedEpochMillis == null) {
            return false;
        }
        try {
            Instant pwdChangedAt = Instant.ofEpochMilli(Long.parseLong(storedEpochMillis));
            boolean revoked = issuedAt.isBefore(pwdChangedAt);
            if (revoked) {
                log.info("Token rejected by password change check: userId={}, tokenIssuedAt={}, pwdChangedAt={}",
                        userId, issuedAt, pwdChangedAt);
            }
            return revoked;
        } catch (NumberFormatException ex) {
            log.warn("Invalid pwdChangedAt value for userId={}: {}", userId, storedEpochMillis);
            return false;
        }
    }

    /**
     * 构造已撤销令牌的缓存键，使用令牌的 SHA-256 哈希值。
     *
     * @param token 令牌字符串
     * @return 缓存键
     */
    private String revokedTokenKey(String token) {
        return REVOKED_TOKEN_KEY_PREFIX + sha256(token.trim());
    }

    /**
     * 构造用户密码变更时间的缓存键。
     *
     * @param userId 用户ID
     * @return 缓存键
     */
    private String pwdChangedAtKey(UUID userId) {
        return PWD_CHANGED_AT_KEY_PREFIX + userId;
    }

    /**
     * 计算字符串的 SHA-256 哈希值。
     *
     * @param value 原始字符串
     * @return 十六进制格式的哈希值
     * @throws IllegalStateException SHA-256 算法不可用时抛出
     */
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