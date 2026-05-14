package com.lqr.paperragserver.common.model;

/**
 * 检索命中的文本片段。
 *
 * @param chunk 命中的文档片段
 * @param rankScore 展示给上层的融合排序分；同时综合向量召回与词法召回顺位
 */
public record RetrievedChunk(
        DocumentChunk chunk,
        double rankScore
) {
}