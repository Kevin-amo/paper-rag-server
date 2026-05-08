package com.lqr.paperragserver.common;

/**
 * 检索命中的文本片段。
 *
 * @param chunk 命中的文档片段
 * @param score 检索相关性分数，数值含义由底层向量检索实现决定
 */
public record RetrievedChunk(
        DocumentChunk chunk,
        double score
) {
}