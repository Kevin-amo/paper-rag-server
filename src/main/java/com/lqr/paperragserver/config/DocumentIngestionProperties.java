package com.lqr.paperragserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文档异步入库配置。
 */
@ConfigurationProperties(prefix = "app.document-ingestion")
public record DocumentIngestionProperties(
        String storageDir,
        Boolean keepUploadFile,
        int maxRetryCount,
        Listener listener
) {

    public DocumentIngestionProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "storage/document-ingestion";
        }
        if (keepUploadFile == null) {
            keepUploadFile = true;
        }
        if (maxRetryCount <= 0) {
            maxRetryCount = 3;
        }
        if (listener == null) {
            listener = new Listener(2, 4);
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
}