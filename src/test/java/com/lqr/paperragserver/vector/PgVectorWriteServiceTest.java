package com.lqr.paperragserver.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.EmbeddingService;
import com.lqr.paperragserver.common.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PgVectorWriteServiceTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PgVectorWriteService service;

    @BeforeEach
    void setUp() {
        service = new PgVectorWriteService(jdbcTemplate, objectMapper);
    }

    @Test
    void upsertShouldWriteVectorAndBackfillChunkReference() {
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1",
                "source-1",
                2,
                "chunk text",
                Map.of("title", "Paper A", "chunkStart", 12, "chunkEnd", 20)
        );
        float[] vector = new float[1536];
        vector[0] = 1.5f;

        service.upsert(List.of(new EmbeddingService.EmbeddingVector(chunk, vector, Map.of("pageNumber", 3))));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(sqlCaptor.capture(), paramCaptor.capture());

        List<String> sqls = sqlCaptor.getAllValues();
        List<MapSqlParameterSource> params = paramCaptor.getAllValues();

        assertThat(sqls.get(0)).contains("insert into public.vector_store");
        assertThat(sqls.get(1)).contains("update public.paper_document_chunk");

        Map<String, Object> insertValues = params.get(0).getValues();
        UUID expectedVectorStoreId = UUID.nameUUIDFromBytes("chunk-1".getBytes(StandardCharsets.UTF_8));
        assertThat(insertValues.get("id")).isEqualTo(expectedVectorStoreId);
        assertThat(String.valueOf(insertValues.get("metadata")))
                .contains("\"sourceId\":\"source-1\"")
                .contains("\"chunkId\":\"chunk-1\"")
                .contains("\"chunkIndex\":2")
                .contains("\"pageNumber\":3");

        Map<String, Object> chunkUpdateValues = params.get(1).getValues();
        assertThat(chunkUpdateValues.get("vectorStoreId")).isEqualTo(expectedVectorStoreId);
        assertThat(chunkUpdateValues.get("chunkId")).isEqualTo("chunk-1");
    }

    @Test
    void upsertShouldRejectWrongVectorDimension() {
        DocumentChunk chunk = new DocumentChunk("chunk-1", "source-1", 0, "chunk text", Map.of());
        float[] vector = new float[1024];

        assertThatThrownBy(() -> service.upsert(List.of(new EmbeddingService.EmbeddingVector(chunk, vector, Map.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1536");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void deleteBySourceIdShouldTargetMatchingMetadata() {
        service.deleteBySourceId("source-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("metadata ->> 'sourceId' = :sourceId");
        assertThat(paramCaptor.getValue().getValues()).containsEntry("sourceId", "source-1");
    }
}