package com.lqr.paperragserver.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class JdbcPaperDocumentPersistenceServiceTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcPaperDocumentPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new JdbcPaperDocumentPersistenceService(jdbcTemplate, objectMapper);
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
    void restoreShouldOnlyRestoreDeletedDocumentAsPending() {
        service.restore("source-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramCaptor.capture());

        assertThat(sqlCaptor.getValue())
                .contains("status = 'PENDING'")
                .contains("deleted_at = null")
                .contains("status = 'DELETED'");
        assertThat(paramCaptor.getValue().getValues()).containsEntry("sourceId", "source-1");
    }
}