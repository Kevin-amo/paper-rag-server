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

    @ConstructorBinding
    public LiteratureSearchProperties {
        if (openalex == null) {
            openalex = new OpenAlex(null, null, null, null);
        }
        if (cache == null) {
            cache = new Cache(null, null);
        }
    }

    public record OpenAlex(
            Boolean enabled,
            String endpoint,
            Duration timeout,
            String mailto
    ) {
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

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }

    public record Cache(
            Boolean enabled,
            Duration ttl
    ) {
        public Cache {
            if (enabled == null) {
                enabled = true;
            }
            if (ttl == null || ttl.isNegative() || ttl.isZero()) {
                ttl = Duration.ofMinutes(20);
            }
        }

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }
}