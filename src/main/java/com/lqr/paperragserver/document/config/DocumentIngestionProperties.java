package com.lqr.paperragserver.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文档异步入库配置。
 */
@ConfigurationProperties(prefix = "app.document-ingestion")
public record DocumentIngestionProperties(
        String storageDir,
        Boolean keepUploadFile,
        int maxRetryCount,
        Listener listener,
        Cleanup cleanup
) {

    public DocumentIngestionProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "storage/document-ingestion";
        }
        if (keepUploadFile == null) {
            keepUploadFile = false;
        }
        if (maxRetryCount <= 0) {
            maxRetryCount = 3;
        }
        if (listener == null) {
            listener = new Listener(2, 4);
        }
        if (cleanup == null) {
            cleanup = new Cleanup(true, Duration.ofHours(24), "0 0 * * * *");
        }
    }

    public record Listener(int concurrency, int maxConcurrency) {
        public Listener {
            if (concurrency <= 0) {
                concurrency = 2;
            }
            if (maxConcurrency < concurrency) {
                maxConcurrency = Math.max(concurrency, 4);
            }
        }
    }

    public record Cleanup(Boolean enabled, Duration retention, String cron) {
        public Cleanup {
            if (enabled == null) {
                enabled = true;
            }
            if (retention == null || retention.isNegative() || retention.isZero()) {
                retention = Duration.ofHours(24);
            }
            if (cron == null || cron.isBlank()) {
                cron = "0 0 * * * *";
            }
        }
    }
}