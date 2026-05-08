package com.lqr.paperragserver.vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.EmbeddingService;
import com.lqr.paperragserver.common.DocumentChunk;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 pgvector 表的向量写入实现。
 */
@Service
public class PgVectorWriteService implements VectorWriteService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PgVectorWriteService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void upsert(List<EmbeddingService.EmbeddingVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return;
        }
        for (EmbeddingService.EmbeddingVector vector : vectors) {
            DocumentChunk chunk = vector.chunk();
            UUID vectorStoreId = UUID.nameUUIDFromBytes(chunk.chunkId().getBytes());
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            if (vector.metadata() != null) {
                metadata.putAll(vector.metadata());
            }
            metadata.put("sourceId", chunk.sourceId());
            metadata.put("chunkId", chunk.chunkId());
            metadata.put("chunkIndex", chunk.chunkIndex());
            jdbcTemplate.update("""
                    insert into public.vector_store (id, content, metadata, embedding)
                    values (:id, :content, cast(:metadata as json), cast(:embedding as vector))
                    on conflict (id) do update set
                        content = excluded.content,
                        metadata = excluded.metadata,
                        embedding = excluded.embedding
                    """, new MapSqlParameterSource()
                    .addValue("id", vectorStoreId)
                    .addValue("content", chunk.content())
                    .addValue("metadata", toJson(metadata))
                    .addValue("embedding", toVectorLiteral(vector.vector())));
            jdbcTemplate.update("""
                    update public.paper_document_chunk
                    set vector_store_id = :vectorStoreId, updated_at = now()
                    where chunk_id = :chunkId
                    """, new MapSqlParameterSource()
                    .addValue("vectorStoreId", vectorStoreId)
                    .addValue("chunkId", chunk.chunkId()));
        }
    }

    @Override
    @Transactional
    public void deleteBySourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                delete from public.vector_store
                where metadata ->> 'sourceId' = :sourceId
                """, new MapSqlParameterSource("sourceId", sourceId));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("向量元数据 JSON 序列化失败", ex);
        }
    }

    private String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("embedding 向量不能为空");
        }
        if (vector.length != 1536) {
            throw new IllegalArgumentException("embedding 向量维度必须为 1536，实际为 " + vector.length);
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