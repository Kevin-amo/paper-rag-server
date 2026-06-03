package com.lqr.paperragserver.auth.service.impl;

import com.lqr.paperragserver.auth.service.LoginAttemptService;
import com.lqr.paperragserver.auth.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;

/**
 * 基于 Redis 的登录失败尝试限制服务。
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final String ACCOUNT_FAILURE_KEY_PREFIX = "auth:login:failure:account:";
    private static final String IP_FAILURE_KEY_PREFIX = "auth:login:failure:ip:";
    private static final String ACCOUNT_LOCK_KEY_PREFIX = "auth:login:lock:account:";
    private static final String IP_LOCK_KEY_PREFIX = "auth:login:lock:ip:";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    /**
     * 如果当前账号或 IP 已被锁定，则直接拒绝本次登录。
     *
     * @param username 用户名
     * @param clientIp 客户端IP地址
     * @throws ResponseStatusException 账号或IP已被锁定时抛出
     */
    @Override
    public void assertNotLocked(String username, String clientIp) {
        SecurityProperties.LoginAttempt config = securityProperties.loginAttempt();
        if (!config.enabled()) {
            return;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(accountLockKey(normalizeUsername(username))))) {
            throw lockedException("账号登录失败次数过多，请稍后再试");
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(ipLockKey(normalizeIp(clientIp))))) {
            throw lockedException("当前 IP 登录失败次数过多，请稍后再试");
        }
    }

    /**
     * 记录一次登录失败，达到阈值时锁定账号与 IP。
     *
     * @param username 用户名
     * @param clientIp 客户端IP地址
     */
    @Override
    public void recordFailure(String username, String clientIp) {
        SecurityProperties.LoginAttempt config = securityProperties.loginAttempt();
        if (!config.enabled()) {
            return;
        }
        String normalizedUsername = normalizeUsername(username);
        String normalizedIp = normalizeIp(clientIp);
        long accountFailures = incrementFailure(accountFailureKey(normalizedUsername), config.window());
        long ipFailures = incrementFailure(ipFailureKey(normalizedIp), config.window());
        if (accountFailures >= config.maxFailures()) {
            redisTemplate.opsForValue().set(accountLockKey(normalizedUsername), "1", config.lockDuration());
        }
        if (ipFailures >= config.maxFailures()) {
            redisTemplate.opsForValue().set(ipLockKey(normalizedIp), "1", config.lockDuration());
        }
    }

    /**
     * 登录成功，清除登录失败尝试次数
     * @param username 用户名
     * @param clientIp 客户端IP
     */
    @Override
    public void clearOnSuccess(String username, String clientIp) {
        SecurityProperties.LoginAttempt config = securityProperties.loginAttempt();
        if (!config.enabled()) {
            return;
        }
        String normalizedUsername = normalizeUsername(username);
        String normalizedIp = normalizeIp(clientIp);
        redisTemplate.delete(accountFailureKey(normalizedUsername));
        redisTemplate.delete(ipFailureKey(normalizedIp));
        redisTemplate.delete(accountLockKey(normalizedUsername));
        redisTemplate.delete(ipLockKey(normalizedIp));
    }

    /**
     * 登录失败计数递增
     * @param key 缓存key
     * @param ttl 缓存有效期
     * @return 递增后的计数
     */
    private long incrementFailure(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count == null ? 0L : count;
    }

    /**
     * 构造登录锁定异常。
     *
     * @param message 异常提示信息
     * @return 429 响应状态异常
     */
    private ResponseStatusException lockedException(String message) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    /**
     * 构造账号失败计数的缓存键。
     *
     * @param normalizedUsername 标准化后的用户名
     * @return 缓存键
     */
    private String accountFailureKey(String normalizedUsername) {
        return ACCOUNT_FAILURE_KEY_PREFIX + normalizedUsername;
    }

    /**
     * 构造IP失败计数的缓存键。
     *
     * @param normalizedIp 标准化后的IP地址
     * @return 缓存键
     */
    private String ipFailureKey(String normalizedIp) {
        return IP_FAILURE_KEY_PREFIX + normalizedIp;
    }

    /**
     * 构造账号锁定状态的缓存键。
     *
     * @param normalizedUsername 标准化后的用户名
     * @return 缓存键
     */
    private String accountLockKey(String normalizedUsername) {
        return ACCOUNT_LOCK_KEY_PREFIX + normalizedUsername;
    }

    /**
     * 构造IP锁定状态的缓存键。
     *
     * @param normalizedIp 标准化后的IP地址
     * @return 缓存键
     */
    private String ipLockKey(String normalizedIp) {
        return IP_LOCK_KEY_PREFIX + normalizedIp;
    }

    /**
     * 标准化用户名，转为小写并去除首尾空白，为空时返回 "unknown"。
     *
     * @param username 用户名
     * @return 标准化后的用户名
     */
    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "unknown";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 标准化IP地址，去除首尾空白，为空时返回 "unknown"。
     *
     * @param clientIp 客户端IP地址
     * @return 标准化后的IP地址
     */
    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }
}