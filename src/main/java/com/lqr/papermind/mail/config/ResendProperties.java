package com.lqr.papermind.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Resend 邮件服务配置。
 *
 * <p>通过 {@code app.mail.resend.*} 属性进行配置，包含启用状态、API Key、
 * 发件人地址、端点和超时时间等参数。</p>
 */
@ConfigurationProperties(prefix = "app.mail.resend")
public record ResendProperties(
        /**
         * 是否启用 Resend 邮件服务。
         */
        Boolean enabled,
        /**
         * Resend API 密钥。
         */
        String apiKey,
        /**
         * 发件人邮箱地址。
         */
        String from,
        /**
         * Resend API 端点地址。
         */
        String endpoint,
        /**
         * HTTP 请求超时时间。
         */
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
