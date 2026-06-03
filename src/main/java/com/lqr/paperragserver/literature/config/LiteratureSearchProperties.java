package com.lqr.paperragserver.literature.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

/**
 * 文献搜索配置。
 */
@ConfigurationProperties(prefix = "app.literature.search")
public record LiteratureSearchProperties(
        OpenAlex openalex,
        Cache cache
) {

    /**
     * 创建文献搜索配置，并为未提供的分组配置补齐默认值。
     */
    @ConstructorBinding
    public LiteratureSearchProperties {
        if (openalex == null) {
            openalex = new OpenAlex(null, null, null, null);
        }
        if (cache == null) {
            cache = new Cache(null, null, null, null, null, null);
        }
    }

    public record OpenAlex(
            Boolean enabled,
            String endpoint,
            Duration timeout,
            String mailto
    ) {
        /**
         * 创建 OpenAlex 配置，并补齐启用状态、接口地址、超时时间和联系邮箱的默认表达。
         */
        public OpenAlex {
            if (enabled == null) {
                enabled = true;
            }
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = "https://api.openalex.org/works";
            } else {
                endpoint = endpoint.trim();
            }
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                timeout = Duration.ofSeconds(10);
            }
            if (mailto != null) {
                mailto = mailto.trim();
                if (mailto.isBlank()) {
                    mailto = null;
                }
            }
        }

        /**
         * 判断 OpenAlex 搜索源是否启用。
         */
        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }

    public record Cache(
            Boolean enabled,
            Duration ttl,
            Duration lockTtl,
            Duration waitRetryInterval,
            Integer waitMaxAttempts,
            Duration ttlJitter
    ) {
        /**
         * 创建缓存配置，并补齐 TTL、锁等待和随机抖动等默认值。
         */
        public Cache {
            if (enabled == null) {
                enabled = true;
            }
            if (ttl == null || ttl.isNegative() || ttl.isZero()) {
                ttl = Duration.ofMinutes(20);
            }
            if (lockTtl == null || lockTtl.isNegative() || lockTtl.isZero()) {
                lockTtl = Duration.ofSeconds(10);
            }
            if (waitRetryInterval == null || waitRetryInterval.isNegative() || waitRetryInterval.isZero()) {
                waitRetryInterval = Duration.ofMillis(100);
            }
            if (waitMaxAttempts == null || waitMaxAttempts < 0) {
                waitMaxAttempts = 5;
            }
            if (ttlJitter == null || ttlJitter.isNegative()) {
                ttlJitter = Duration.ofMinutes(2);
            }
        }

        /**
         * 判断文献搜索缓存是否启用。
         */
        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }
}