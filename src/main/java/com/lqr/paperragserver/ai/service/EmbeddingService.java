package com.lqr.paperragserver.ai.service;

import com.lqr.paperragserver.common.model.DocumentChunk;

import java.util.List;
import java.util.Map;

/**
 * 向量嵌入服务接口。
 *
 * <p>实现类负责把切分后的文档片段转换为向量表示，供后续写入向量库使用。</p>
 */
public interface EmbeddingService {

    /**
     * 计算一组文档片段的向量。
     *
     * @param chunks 待计算向量的文档片段
     * @return 片段与向量的对应结果列表
     */
    List<EmbeddingVector> embed(List<DocumentChunk> chunks);

    /**
     * 单个片段的向量结果。
     *
     * @param chunk 原始片段
     * @param vector 片段向量
     * @param metadata 向量相关扩展信息
     */
    record EmbeddingVector(
            DocumentChunk chunk,
            float[] vector,
            Map<String, Object> metadata
    ) {
    }
}