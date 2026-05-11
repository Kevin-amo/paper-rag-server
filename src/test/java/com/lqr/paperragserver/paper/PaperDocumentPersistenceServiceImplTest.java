package com.lqr.paperragserver.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.paper.impl.PaperDocumentPersistenceServiceImpl;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperDocumentPersistenceServiceImplTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaperDocumentPersistenceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaperDocumentPersistenceServiceImpl(jdbcTemplate, objectMapper);
    }

    @Test
    void listDocumentsShouldExcludeDeletedByDefaultAndSearchKeyword() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        PaperDocumentPersistenceService.PageResult<PaperDocumentPersistenceService.DocumentSummary> result =
                service.listDocuments("paper", null, 0, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class));

        assertThat(result.total()).isZero();
        assertThat(sqlCaptor.getValue())
                .contains("status <> 'DELETED'")
                .contains("deleted_at is null")
                .contains("title ilike :keyword")
                .contains("source_id ilike :keyword")
                .contains("file_name ilike :keyword")
                .contains("doi ilike :keyword");
        assertThat(paramCaptor.getValue().getValues())
                .containsEntry("keyword", "%paper%")
                .containsEntry("limit", 20)
                .containsEntry("offset", 0);
    }

    @Test
    void listDocumentsShouldRespectExplicitStatus() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        service.listDocuments(null, "indexed", 2, 10);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramCaptor.capture(), any(RowMapper.class));

        assertThat(sqlCaptor.getValue()).contains("status = :status");
        assertThat(paramCaptor.getValue().getValues())
                .containsEntry("status", "INDEXED")
                .containsEntry("limit", 10)
                .containsEntry("offset", 20);
    }

    @Test
    void updateMetadataShouldSerializeJsonFieldsAndMergeMetadata() {
        PaperDocumentPersistenceService.DocumentMetadataUpdate update = new PaperDocumentPersistenceService.DocumentMetadataUpdate(
                "Paper A",
                List.of("Alice", "Bob"),
                "abstract",
                "10.1000/demo",
                "Journal",
                2024,
                List.of("rag", "paper"),
                Map.of("source", "manual")
        );

        service.updateMetadata("source-1", update);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramCaptor.capture());

        Map<String, Object> values = paramCaptor.getValue().getValues();
        assertThat(sqlCaptor.getValue())
                .contains("metadata = metadata || cast(:metadata as jsonb)")
                .contains("updated_at = now()");
        assertThat(values)
                .containsEntry("sourceId", "source-1")
                .containsEntry("title", "Paper A")
                .containsEntry("publishYear", 2024);
        assertThat(String.valueOf(values.get("authors"))).contains("Alice", "Bob");
        assertThat(String.valueOf(values.get("keywords"))).contains("rag", "paper");
        assertThat(String.valueOf(values.get("metadata"))).contains("manual");
    }

    @Test
    void replaceChunksShouldTrimSectionTitleToDatabaseLimit() {
        String longSectionTitle = "A".repeat(600);
        DocumentChunk chunk = new DocumentChunk(
                "chunk-1",
                "source-1",
                0,
                "chunk text",
                Map.of("sectionTitle", longSectionTitle)
        );

        service.replaceChunks("source-1", List.of(chunk));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), batchCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("insert into public.paper_document_chunk");
        Map<String, Object> values = batchCaptor.getValue()[0].getValues();
        assertThat(String.valueOf(values.get("sectionTitle")))
                .hasSize(512)
                .isEqualTo(longSectionTitle.substring(0, 512));
    }

    @Test
    void markIndexedShouldClearDeletedAtMarker() {
        service.markIndexed("source-1", 12);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("status = 'INDEXED'")
                .contains("deleted_at = null")
                .contains("chunk_count = :chunkCount");
        assertThat(paramCaptor.getValue().getValues())
                .containsEntry("sourceId", "source-1")
                .containsEntry("chunkCount", 12);
    }

    @Test
    void restoreShouldOnlyRestoreDeletedDocumentAsPending() {
        service.restore("source-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("status = 'PENDING'")
                .contains("deleted_at = null")
                .contains("deleted_at is not null");
        assertThat(paramCaptor.getValue().getValues()).containsEntry("sourceId", "source-1");
    }
}
