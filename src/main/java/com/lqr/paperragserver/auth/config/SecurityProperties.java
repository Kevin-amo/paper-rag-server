package com.lqr.paperragserver.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 登录与权限配置。
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Jwt jwt,
        Cors cors,
        BootstrapAdmin bootstrapAdmin,
        RegisterEmailCode registerEmailCode,
        LoginAttempt loginAttempt
) {
    public SecurityProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null);
        }
        if (cors == null) {
            cors = new Cors(null, null, null, null);
        }
        if (bootstrapAdmin == null) {
            bootstrapAdmin = new BootstrapAdmin(false, null, null, null);
        }
        if (registerEmailCode == null) {
            registerEmailCode = new RegisterEmailCode(null, null, 0, 0, 0);
        }
        if (loginAttempt == null) {
            loginAttempt = new LoginAttempt(true, 0, null, null);
        }
    }

    /**
     * JWT 访问令牌配置。
     */
    public record Jwt(
            String issuer,
            String secret,
            Duration accessTokenTtl
    ) {
        private static final String DEFAULT_SECRET = "V1kNrJuT67FGQp0fWCJlkzH3KdavGcGYoUz6TdBzheIOVu7E5HfJQVxulvmIh73E";

        public Jwt {
            if (issuer == null || issuer.isBlank()) {
                issuer = "paper-rag-server";
            }
            if (secret == null || secret.isBlank()) {
                secret = DEFAULT_SECRET;
            }
            if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
                accessTokenTtl = Duration.ofHours(2);
            }
        }
    }

    /**
     * 默认管理员初始化配置。
     */
    public record BootstrapAdmin(
            boolean enabled,
            String username,
            String password,
            String displayName
    ) {
        public BootstrapAdmin {
            if (username == null || username.isBlank()) {
                username = "admin";
            }
            if (displayName == null || displayName.isBlank()) {
                displayName = "系统管理员";
            }
        }
    }

    /**
     * 注册邮箱验证码与发送频控配置。
     */
    public record RegisterEmailCode(
            Duration codeTtl,
            Duration emailCooldown,
            int emailDailyLimit,
            int ipMinuteLimit,
            int ipDailyLimit
    ) {
        public RegisterEmailCode {
            if (codeTtl == null || codeTtl.isNegative() || codeTtl.isZero()) {
                codeTtl = Duration.ofMinutes(5);
            }
            if (emailCooldown == null || emailCooldown.isNegative() || emailCooldown.isZero()) {
                emailCooldown = Duration.ofSeconds(60);
            }
            if (emailDailyLimit <= 0) {
                emailDailyLimit = 10;
            }
            if (ipMinuteLimit <= 0) {
                ipMinuteLimit = 20;
            }
            if (ipDailyLimit <= 0) {
                ipDailyLimit = 200;
            }
        }
    }

    /**
     * 登录失败尝试锁定配置。
     */
    public record LoginAttempt(
            boolean enabled,
            int maxFailures,
            Duration window,
            Duration lockDuration
    ) {
        public LoginAttempt {
            if (maxFailures <= 0) {
                maxFailures = 5;
            }
            if (window == null || window.isNegative() || window.isZero()) {
                window = Duration.ofMinutes(10);
            }
            if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
                lockDuration = Duration.ofMinutes(10);
            }
        }
    }

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            Duration maxAge
    ) {
        public Cors {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                allowedOrigins = List.of("http://localhost:5173");
            }
            if (allowedMethods == null || allowedMethods.isEmpty()) {
                allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
            }
            if (allowedHeaders == null || allowedHeaders.isEmpty()) {
                allowedHeaders = List.of("Authorization", "Content-Type");
            }
            if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
                maxAge = Duration.ofHours(1);
            }
        }
    }

}