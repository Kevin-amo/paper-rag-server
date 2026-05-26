package com.lqr.paperragserver.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

/**
 * RAG 业务配置
 * 这些配置控制文档切分、检索数量和相似度阈值，避免业务实现中写死参数
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        /* 单个文本片段的最大字符数。 */
        int chunkSize,
        /* 相邻文本片段之间保留的重叠字符数。 */
        int chunkOverlap,
        /* 问答检索时默认召回的片段数量。 */
        int defaultTopK,
        /* 向量检索相似度阈值，0 表示接受全部结果。 */
        double similarityThreshold,
        /* 精排序配置。 */
        RerankProperties rerank
) {

    public RagProperties(int chunkSize, int chunkOverlap, int defaultTopK, double similarityThreshold) {
        this(chunkSize, chunkOverlap, defaultTopK, similarityThreshold, null);
    }

    /**
     * 提供默认配置，防止配置文件缺失时服务无法启动。
     */
    @ConstructorBinding
    public RagProperties {
        if (chunkSize <= 0) {
            chunkSize = 800;
        }
        if (chunkOverlap < 0) {
            chunkOverlap = 120;
        }
        if (chunkOverlap >= chunkSize) {
            chunkOverlap = Math.max(0, chunkSize / 5);
        }
        if (defaultTopK <= 0) {
            defaultTopK = 3;
        }
        if (similarityThreshold < 0) {
            similarityThreshold = 0;
        }
        if (rerank == null) {
            rerank = new RerankProperties(true, "gte-rerank-v2", 5, 3, Duration.ofSeconds(10), "https://dashscope.aliyuncs.com");
        }
    }

    public record RerankProperties(
            boolean enabled,
            String model,
            int topN,
            int candidateMultiplier,
            Duration timeout,
            String baseUrl
    ) {

        public RerankProperties {
            if (model == null || model.isBlank()) {
                model = "qwen3-rerank";
            }
            if (topN <= 0) {
                topN = 5;
            }
            if (candidateMultiplier <= 0) {
                candidateMultiplier = 3;
            }
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                timeout = Duration.ofSeconds(10);
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://dashscope.aliyuncs.com";
            }
        }
    }
}