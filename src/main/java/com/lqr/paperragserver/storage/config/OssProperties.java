package com.lqr.paperragserver.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * 阿里云 OSS 对象存储配置。
 */
@ConfigurationProperties(prefix = "app.storage.oss")
public record OssProperties(
        String endpoint,
        String bucket,
        String accessKeyId,
        String accessKeySecret,
        String publicBaseUrl,
        String avatarPrefix,
        DataSize avatarMaxSize,
        List<String> allowedContentTypes
) {

    public OssProperties {
        if (avatarPrefix == null || avatarPrefix.isBlank()) {
            avatarPrefix = "avatars";
        }
        avatarPrefix = trimSlashes(avatarPrefix);
        if (avatarMaxSize == null || avatarMaxSize.isNegative() || avatarMaxSize.toBytes() == 0) {
            avatarMaxSize = DataSize.ofMegabytes(5);
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");
        } else {
            allowedContentTypes = allowedContentTypes.stream()
                    .filter(type -> type != null && !type.isBlank())
                    .map(type -> type.trim().toLowerCase())
                    .distinct()
                    .toList();
            if (allowedContentTypes.isEmpty()) {
                allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");
            }
        }
    }

    public String normalizedPublicBaseUrl() {
        return publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return hasText(endpoint) && hasText(bucket) && hasText(accessKeyId) && hasText(accessKeySecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}