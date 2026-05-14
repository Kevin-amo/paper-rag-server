package com.lqr.paperragserver.common.model;

import java.util.Map;

/**
 * 文档切分后的文本片段。
 *
 * @param chunkId 片段唯一标识，用于向量库写入和引用回溯
 * @param sourceId 所属文档来源标识
 * @param chunkIndex 片段在原文中的顺序号
 * @param content 片段文本内容
 * @param metadata 片段级扩展元数据，例如页码、章节、字符范围等信息
 */
public record DocumentChunk(
        String chunkId,
        String sourceId,
        int chunkIndex,
        String content,
        Map<String, Object> metadata
) {
}