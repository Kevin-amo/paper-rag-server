package com.lqr.paperragserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 登录与权限配置。
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Jwt jwt,
        BootstrapAdmin bootstrapAdmin
) {
    public SecurityProperties {
        if (jwt == null) {
            jwt = new Jwt(null, null, null);
        }
        if (bootstrapAdmin == null) {
            bootstrapAdmin = new BootstrapAdmin(false, null, null, null);
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
        private static final String DEFAULT_SECRET = "paper-rag-server-local-development-secret-change-before-production";

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
}