package com.lqr.paperragserver.ai.service.impl;

import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI EmbeddingModel 的向量计算实现。
 */
@Slf4j
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
            log.info("embedding.start chunkCount={} sourceIdDistribution={} totalChars={}", 0, Map.of(), 0);
            log.info("embedding.done chunkCount={} vectorCount={} sourceIdDistribution={} totalChars={} costMs={}", 0, 0, Map.of(), 0, 0);
            return List.of();
        }
        long startNanos = System.nanoTime();
        Map<String, Long> sourceDistribution = sourceDistribution(chunks);
        int totalChars = chunks.stream().mapToInt(chunk -> chunk.content() == null ? 0 : chunk.content().length()).sum();
        log.info("embedding.start chunkCount={} sourceIdDistribution={} totalChars={}", chunks.size(), sourceDistribution, totalChars);
        try {
            List<EmbeddingVector> vectors = new ArrayList<>(chunks.size());
            for (DocumentChunk chunk : chunks) {
                float[] vector = embeddingModel.embed(chunk.content());
                vectors.add(new EmbeddingVector(chunk, vector, chunk.metadata() == null ? Map.of() : chunk.metadata()));
            }
            log.info("embedding.done chunkCount={} vectorCount={} sourceIdDistribution={} totalChars={} costMs={}",
                    chunks.size(), vectors.size(), sourceDistribution, totalChars, elapsedMs(startNanos));
            return vectors;
        } catch (RuntimeException ex) {
            log.error("embedding.failed chunkCount={} sourceIdDistribution={} totalChars={} costMs={}",
                    chunks.size(), sourceDistribution, totalChars, elapsedMs(startNanos), ex);
            throw ex;
        }
    }

    /**
     * 按来源 ID 统计文档分块的数量分布。
     *
     * @param chunks 文档分块列表
     * @return 来源 ID 到分块数量的映射
     */
    private Map<String, Long> sourceDistribution(List<DocumentChunk> chunks) {
        return chunks.stream()
                .collect(Collectors.groupingBy(DocumentChunk::sourceId, Collectors.counting()));
    }

    /**
     * 计算从指定起始时间到当前经过的毫秒数。
     *
     * @param startNanos 起始纳秒时间戳
     * @return 经过的毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}