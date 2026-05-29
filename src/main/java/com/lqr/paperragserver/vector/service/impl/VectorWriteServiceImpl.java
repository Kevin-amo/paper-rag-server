package com.lqr.paperragserver.vector.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.EmbeddingService;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.document.mapper.DocumentChunkMapper;
import com.lqr.paperragserver.vector.mapper.VectorStoreMapper;
import com.lqr.paperragserver.vector.service.VectorWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 基于 pgvector 表的向量写入实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorWriteServiceImpl implements VectorWriteService {

    private final VectorStoreMapper vectorStoreMapper;
    private final DocumentChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;
    @Value("${spring.ai.vectorstore.pgvector.dimensions:1536}")
    private int embeddingDimensions;

    /**
     * 将分块向量写入向量表并回填文档分块记录。
     *
     * @param vectors 待写入的向量列表
     */
    @Override
    @Transactional
    public void upsert(UUID ownerUserId, List<EmbeddingService.EmbeddingVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            log.info("vector.write.start ownerUserId={} vectorCount={} sourceIdDistribution={}", ownerUserId, 0, Map.of());
            log.info("vector.write.done ownerUserId={} vectorCount={} sourceIdDistribution={} embeddingDimensions={} costMs={}",
                    ownerUserId, 0, Map.of(), embeddingDimensions, 0);
            return;
        }
        long startNanos = System.nanoTime();
        Map<String, Long> sourceDistribution = sourceDistribution(vectors);
        log.info("vector.write.start ownerUserId={} vectorCount={} sourceIdDistribution={}", ownerUserId, vectors.size(), sourceDistribution);
        try {
            for (EmbeddingService.EmbeddingVector vector : vectors) {
                DocumentChunk chunk = vector.chunk();
                UUID vectorStoreId = UUID.nameUUIDFromBytes((ownerUserId + "::" + chunk.chunkId()).getBytes(StandardCharsets.UTF_8));
                Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                if (vector.metadata() != null) {
                    metadata.putAll(vector.metadata());
                }
                metadata.put(MetadataKeys.OWNER_USER_ID, ownerUserId.toString());
                metadata.put(MetadataKeys.SOURCE_ID, chunk.sourceId());
                metadata.put(MetadataKeys.CHUNK_ID, chunk.chunkId());
                metadata.put(MetadataKeys.CHUNK_INDEX, chunk.chunkIndex());
                vectorStoreMapper.upsert(
                        vectorStoreId,
                        chunk.content(),
                        toJson(metadata),
                        toVectorLiteral(vector.vector())
                );
                chunkMapper.updateVectorStoreId(ownerUserId, chunk.chunkId(), vectorStoreId);
            }
            log.info("vector.write.done ownerUserId={} vectorCount={} sourceIdDistribution={} embeddingDimensions={} costMs={}",
                    ownerUserId, vectors.size(), sourceDistribution, embeddingDimensions, elapsedMs(startNanos));
        } catch (RuntimeException ex) {
            log.error("vector.write.failed ownerUserId={} vectorCount={} sourceIdDistribution={} embeddingDimensions={} costMs={}",
                    ownerUserId, vectors.size(), sourceDistribution, embeddingDimensions, elapsedMs(startNanos), ex);
            throw ex;
        }
    }

    /**
     * 按文档来源删除向量表中的全部记录。
     *
     * @param sourceId 文档来源标识
     */
    @Override
    @Transactional
    public void deleteBySourceId(UUID ownerUserId, String sourceId) {
        if (ownerUserId == null || sourceId == null || sourceId.isBlank()) {
            log.warn("vector.delete.skipped ownerUserId={} sourceId={} reason=INVALID_ARGUMENT", ownerUserId, sourceId);
            return;
        }
        long startNanos = System.nanoTime();
        vectorStoreMapper.deleteBySourceId(ownerUserId.toString(), sourceId);
        log.info("vector.delete.done ownerUserId={} sourceId={} costMs={}", ownerUserId, sourceId, elapsedMs(startNanos));
    }

    private Map<String, Long> sourceDistribution(List<EmbeddingService.EmbeddingVector> vectors) {
        return vectors.stream()
                .map(vector -> vector.chunk().sourceId())
                .collect(Collectors.groupingBy(sourceId -> sourceId, Collectors.counting()));
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 文本
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("向量元数据 JSON 序列化失败", ex);
        }
    }

    /**
     * 将浮点向量转换为 pgvector 可识别的字面量。
     *
     * @param vector 向量数组
     * @return pgvector 字面量字符串
     */
    private String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("embedding 向量不能为空");
        }
        if (vector.length != embeddingDimensions) {
            throw new IllegalArgumentException("embedding 向量维度必须为 " + embeddingDimensions + "，实际为 " + vector.length);
        }
        StringBuilder builder = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}