package com.lqr.paperragserver.auth.service.impl;

import com.lqr.paperragserver.auth.config.SecurityProperties;
import com.lqr.paperragserver.auth.service.VerificationCodeService;
import com.lqr.paperragserver.mail.service.MailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务，按场景和通道隔离 Redis key，后续可直接扩展手机号登录验证码。
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCodeService.class);
    private static final Duration MINUTE_COUNTER_TTL = Duration.ofSeconds(60);
    private static final Duration DAILY_COUNTER_TTL = Duration.ofDays(1);
    private static final String REGISTER_EMAIL_CODE_KEY_PREFIX = "auth:code:register:email:";
    private static final String REGISTER_EMAIL_COOLDOWN_KEY_PREFIX = "auth:code:register:email:cooldown:";
    private static final String REGISTER_EMAIL_DAILY_KEY_PREFIX = "auth:code:register:email:daily:";
    private static final String REGISTER_IP_MINUTE_KEY_PREFIX = "auth:code:register:ip:minute:";
    private static final String REGISTER_IP_DAILY_KEY_PREFIX = "auth:code:register:ip:daily:";
    private static final String CHANGE_EMAIL_CODE_KEY_PREFIX = "auth:code:change-email:";
    private static final String CHANGE_EMAIL_COOLDOWN_KEY_PREFIX = "auth:code:change-email:cooldown:";
    private static final String CHANGE_EMAIL_DAILY_KEY_PREFIX = "auth:code:change-email:daily:";
    private static final String CHANGE_EMAIL_IP_MINUTE_KEY_PREFIX = "auth:code:change-email:ip:minute:";
    private static final String CHANGE_EMAIL_IP_DAILY_KEY_PREFIX = "auth:code:change-email:ip:daily:";

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final MailService mailService;

    @Override
    public void createRegisterEmailCode(String email, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedIp = normalizeIp(clientIp);
        SecurityProperties.RegisterEmailCode config = securityProperties.registerEmailCode();

        if (Boolean.TRUE.equals(redisTemplate.hasKey(registerEmailCooldownKey(normalizedEmail)))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }
        requireWithinLimit(registerEmailDailyKey(normalizedEmail), DAILY_COUNTER_TTL, config.emailDailyLimit(), "该邮箱今日验证码发送次数已达上限");
        requireWithinLimit(registerIpMinuteKey(normalizedIp), MINUTE_COUNTER_TTL, config.ipMinuteLimit(), "当前 IP 验证码请求过于频繁，请稍后再试");
        requireWithinLimit(registerIpDailyKey(normalizedIp), DAILY_COUNTER_TTL, config.ipDailyLimit(), "当前 IP 今日验证码发送次数已达上限");

        String code = "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
        String codeKey = registerEmailCodeKey(normalizedEmail);
        String cooldownKey = registerEmailCooldownKey(normalizedEmail);
        redisTemplate.opsForValue().set(codeKey, code, config.codeTtl());
        redisTemplate.opsForValue().set(cooldownKey, "1", config.emailCooldown());
        try {
            mailService.sendRegisterEmailCode(normalizedEmail, code, config.codeTtl());
            log.info("邮箱注册验证码邮件已发送：email={}, ip={}, ttl={}s", normalizedEmail, normalizedIp, config.codeTtl().toSeconds());
        } catch (RuntimeException ex) {
            redisTemplate.delete(List.of(codeKey, cooldownKey));
            log.warn("邮箱注册验证码邮件发送失败：email={}, ip={}", normalizedEmail, normalizedIp, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "验证码邮件发送失败，请稍后再试", ex);
        }
    }

    @Override
    public void createChangeEmailCode(String email, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedIp = normalizeIp(clientIp);
        SecurityProperties.RegisterEmailCode config = securityProperties.registerEmailCode();

        if (Boolean.TRUE.equals(redisTemplate.hasKey(changeEmailCooldownKey(normalizedEmail)))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "验证码发送过于频繁，请稍后再试");
        }
        requireWithinLimit(changeEmailDailyKey(normalizedEmail), DAILY_COUNTER_TTL, config.emailDailyLimit(), "该邮箱今日验证码发送次数已达上限");
        requireWithinLimit(changeEmailIpMinuteKey(normalizedIp), MINUTE_COUNTER_TTL, config.ipMinuteLimit(), "当前 IP 验证码请求过于频繁，请稍后再试");
        requireWithinLimit(changeEmailIpDailyKey(normalizedIp), DAILY_COUNTER_TTL, config.ipDailyLimit(), "当前 IP 今日验证码发送次数已达上限");

        String code = "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
        String codeKey = changeEmailCodeKey(normalizedEmail);
        String cooldownKey = changeEmailCooldownKey(normalizedEmail);
        redisTemplate.opsForValue().set(codeKey, code, config.codeTtl());
        redisTemplate.opsForValue().set(cooldownKey, "1", config.emailCooldown());
        try {
            mailService.sendChangeEmailCode(normalizedEmail, code, config.codeTtl());
            log.info("邮箱换绑验证码邮件已发送：email={}, ip={}, ttl={}s", normalizedEmail, normalizedIp, config.codeTtl().toSeconds());
        } catch (RuntimeException ex) {
            redisTemplate.delete(List.of(codeKey, cooldownKey));
            log.warn("邮箱换绑验证码邮件发送失败：email={}, ip={}", normalizedEmail, normalizedIp, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "验证码邮件发送失败，请稍后再试", ex);
        }
    }

    @Override
    public void requireRegisterEmailCodeMatches(String email, String code) {
        requireCodeMatches(registerEmailCodeKey(normalizeEmail(email)), code);
    }

    @Override
    public void requireChangeEmailCodeMatches(String email, String code) {
        requireCodeMatches(changeEmailCodeKey(normalizeEmail(email)), code);
    }

    @Override
    public void deleteRegisterEmailCode(String email) {
        redisTemplate.delete(registerEmailCodeKey(normalizeEmail(email)));
    }

    @Override
    public void deleteChangeEmailCode(String email) {
        redisTemplate.delete(changeEmailCodeKey(normalizeEmail(email)));
    }

    private void requireCodeMatches(String codeKey, String code) {
        String expectedCode = redisTemplate.opsForValue().get(codeKey);
        if (expectedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码已过期或不存在");
        }
        if (!expectedCode.equals(code == null ? null : code.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱或验证码不正确");
        }
    }

    private String registerEmailCodeKey(String normalizedEmail) {
        return REGISTER_EMAIL_CODE_KEY_PREFIX + normalizedEmail;
    }

    private String registerEmailCooldownKey(String normalizedEmail) {
        return REGISTER_EMAIL_COOLDOWN_KEY_PREFIX + normalizedEmail;
    }

    private String registerEmailDailyKey(String normalizedEmail) {
        return REGISTER_EMAIL_DAILY_KEY_PREFIX + normalizedEmail;
    }

    private String registerIpMinuteKey(String normalizedIp) {
        return REGISTER_IP_MINUTE_KEY_PREFIX + normalizedIp;
    }

    private String registerIpDailyKey(String normalizedIp) {
        return REGISTER_IP_DAILY_KEY_PREFIX + normalizedIp;
    }

    private String changeEmailCodeKey(String normalizedEmail) {
        return CHANGE_EMAIL_CODE_KEY_PREFIX + normalizedEmail;
    }

    private String changeEmailCooldownKey(String normalizedEmail) {
        return CHANGE_EMAIL_COOLDOWN_KEY_PREFIX + normalizedEmail;
    }

    private String changeEmailDailyKey(String normalizedEmail) {
        return CHANGE_EMAIL_DAILY_KEY_PREFIX + normalizedEmail;
    }

    private String changeEmailIpMinuteKey(String normalizedIp) {
        return CHANGE_EMAIL_IP_MINUTE_KEY_PREFIX + normalizedIp;
    }

    private String changeEmailIpDailyKey(String normalizedIp) {
        return CHANGE_EMAIL_IP_DAILY_KEY_PREFIX + normalizedIp;
    }

    private void requireWithinLimit(String key, Duration ttl, int limit, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }
}
