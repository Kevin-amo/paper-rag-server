package com.lqr.paperragserver.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Resend 邮件服务配置。
 */
@ConfigurationProperties(prefix = "app.mail.resend")
public record ResendProperties(
        Boolean enabled,
        String apiKey,
        String from,
        String endpoint,
        Duration timeout
) {
    public ResendProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://api.resend.com/emails";
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(10);
        }
    }
}