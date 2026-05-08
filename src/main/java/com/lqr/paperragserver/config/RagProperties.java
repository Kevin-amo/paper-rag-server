package com.lqr.paperragserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 业务配置
 *
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
        double similarityThreshold
) {

    /**
     * 提供默认配置，防止配置文件缺失时服务无法启动。
     */
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
            defaultTopK = 5;
        }
        if (similarityThreshold < 0) {
            similarityThreshold = 0;
        }
    }
}