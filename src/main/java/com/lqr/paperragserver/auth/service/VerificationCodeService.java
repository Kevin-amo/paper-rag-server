package com.lqr.paperragserver.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务，按场景和通道隔离 Redis key，后续可直接扩展手机号登录验证码。
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);
    private static final Duration REGISTER_EMAIL_CODE_TTL = Duration.ofMinutes(5);
    private static final String REGISTER_EMAIL_CODE_KEY_PREFIX = "auth:code:register:email:";

    private final StringRedisTemplate redisTemplate;

    public void createRegisterEmailCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        String code = "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
        redisTemplate.opsForValue().set(registerEmailCodeKey(normalizedEmail), code, REGISTER_EMAIL_CODE_TTL);
        log.info("邮箱注册验证码已生成：email={}, code={}, ttl={}s", normalizedEmail, code, REGISTER_EMAIL_CODE_TTL.toSeconds());
    }

    public void requireRegisterEmailCodeMatches(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String expectedCode = redisTemplate.opsForValue().get(registerEmailCodeKey(normalizedEmail));
        if (expectedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期或不存在");
        }
        if (!expectedCode.equals(code == null ? null : code.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱或验证码不正确");
        }
    }

    public void deleteRegisterEmailCode(String email) {
        redisTemplate.delete(registerEmailCodeKey(normalizeEmail(email)));
    }

    private String registerEmailCodeKey(String normalizedEmail) {
        return REGISTER_EMAIL_CODE_KEY_PREFIX + normalizedEmail;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}