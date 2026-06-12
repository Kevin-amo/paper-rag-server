package com.lqr.papermind.literature.config;

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

    /**
     * OpenAlex 文献搜索源配置。
     *
     * @param enabled 是否启用 OpenAlex 搜索
     * @param endpoint OpenAlex API 接口地址
     * @param timeout 请求超时时间
     * @param mailto 联系邮箱，用于 OpenAlex polite pool
     */
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

    /**
     * 文献搜索缓存配置。
     *
     * @param enabled 是否启用缓存
     * @param ttl 缓存过期时间
     * @param lockTtl 缓存重建锁过期时间
     * @param waitRetryInterval 等待缓存重建的重试间隔
     * @param waitMaxAttempts 等待缓存重建的最大重试次数
     * @param ttlJitter 缓存过期时间随机抖动范围
     */
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