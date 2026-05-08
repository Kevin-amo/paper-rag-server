package com.lqr.paperragserver.ai;

import com.lqr.paperragserver.common.DocumentChunk;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 Spring AI EmbeddingModel 的向量计算实现。
 */
@Service
public class SpringAiEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

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