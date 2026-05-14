package com.lqr.paperragserver.ai.impl;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Spring AI EmbeddingModel 的向量计算实现。
 */
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * 为文档分块批量生成向量表示。
     *
     * @param chunks 待向量化的分块列表
     * @return 与输入分块顺序一致的向量结果列表
     */
    @Override
    public List<EmbeddingVector> embed(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<EmbeddingVector> vectors = new ArrayList<>(chunks.size());
        for (DocumentChunk chunk : chunks) {
            float[] vector = embeddingModel.embed(chunk.content());
            vectors.add(new EmbeddingVector(chunk, vector, chunk.metadata() == null ? Map.of() : chunk.metadata()));
        }
        return vectors;
    }
}