package com.lqr.paperragserver.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC 实现的文档元数据持久化服务。
 */
@Service
public class JdbcPaperDocumentPersistenceService implements PaperDocumentPersistenceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPaperDocumentPersistenceService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<DocumentSummary> listDocuments(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, 1, 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", normalizeLikeKeyword(keyword))
                .addValue("status", normalizeStatus(status))
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);
        String whereSql = documentWhereSql(keyword, status);
        Long total = jdbcTemplate.queryForObject("""
                select count(*)
                from public.paper_document
                """ + whereSql, params, Long.class);
        List<DocumentSummary> items = jdbcTemplate.query("""
                select source_id, title, origin, file_name, file_type, file_size, status, chunk_count,
                       publish_year, created_at, updated_at
                from public.paper_document
                """ + whereSql + """
                order by updated_at desc, created_at desc
                limit :limit offset :offset
                """, params, new DocumentSummaryRowMapper());
        return new PageResult<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Override
    public Optional<DocumentDetail> findDocument(String sourceId) {
        List<DocumentDetail> results = jdbcTemplate.query("""
                select source_id, title, origin, file_name, file_type, file_size,
                       authors, abstract, doi, journal, publish_year, keywords,
                       content_text, metadata, status, chunk_count, error_message,
                       created_at, updated_at, deleted_at
                from public.paper_document
                where source_id = :sourceId
                """, new MapSqlParameterSource("sourceId", sourceId), new DocumentDetailRowMapper());
        return results.stream().findFirst();
    }

    @Override
    public PageResult<DocumentChunkView> listChunks(String sourceId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, 1, 200);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("limit", safeSize)
                .addValue("offset", safePage * safeSize);
        Long total = jdbcTemplate.queryForObject("""
                select count(*)
                from public.paper_document_chunk
                where source_id = :sourceId
                """, params, Long.class);
        List<DocumentChunkView> items = jdbcTemplate.query("""
                select chunk_id, chunk_index, content, content_hash, chunk_start, chunk_end,
                       page_number, section_title, metadata, vector_store_id, created_at, updated_at
                from public.paper_document_chunk
                where source_id = :sourceId
                order by chunk_index asc
                limit :limit offset :offset
                """, params, new DocumentChunkViewRowMapper());
        return new PageResult<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Override
    public void updateMetadata(String sourceId, DocumentMetadataUpdate update) {
        Map<String, Object> metadata = update.metadata() == null ? Map.of() : update.metadata();
        jdbcTemplate.update("""
                update public.paper_document
                set title = coalesce(:title, title),
                    authors = coalesce(cast(:authors as jsonb), authors),
                    abstract = coalesce(:abstractText, abstract),
                    doi = coalesce(:doi, doi),
                    journal = coalesce(:journal, journal),
                    publish_year = coalesce(:publishYear, publish_year),
                    keywords = coalesce(cast(:keywords as jsonb), keywords),
                    metadata = metadata || cast(:metadata as jsonb),
                    updated_at = now()
                where source_id = :sourceId
                """, new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("title", blankToNull(update.title()))
                .addValue("authors", toNullableJson(update.authors()))
                .addValue("abstractText", blankToNull(update.abstractText()))
                .addValue("doi", blankToNull(update.doi()))
                .addValue("journal", blankToNull(update.journal()))
                .addValue("publishYear", update.publishYear())
                .addValue("keywords", toNullableJson(update.keywords()))
                .addValue("metadata", toJson(metadata)));
    }

    @Override
    public void restore(String sourceId) {
        jdbcTemplate.update("""
                update public.paper_document
                set status = 'PENDING', deleted_at = null, updated_at = now()
                where source_id = :sourceId and status = 'DELETED'
                """, new MapSqlParameterSource("sourceId", sourceId));
    }

    @Override
    @Transactional
    public void markParsing(DocumentSource source, String contentText) {
        Map<String, Object> metadata = source.metadata() == null ? Map.of() : source.metadata();
        jdbcTemplate.update("""
                insert into public.paper_document (
                    source_id, title, origin, file_name, file_type, file_size, content_text, metadata, status, chunk_count, error_message
                ) values (
                    :sourceId, :title, :origin, :fileName, :fileType, :fileSize, :contentText, cast(:metadata as jsonb), 'PARSING', 0, null
                )
                on conflict (source_id) do update set
                    title = excluded.title,
                    origin = excluded.origin,
                    file_name = excluded.file_name,
                    file_type = excluded.file_type,
                    file_size = excluded.file_size,
                    content_text = excluded.content_text,
                    metadata = excluded.metadata,
                    status = 'PARSING',
                    chunk_count = 0,
                    error_message = null,
                    deleted_at = null,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("sourceId", source.sourceId())
                .addValue("title", nonBlank(source.title(), source.origin(), source.sourceId()))
                .addValue("origin", source.origin())
                .addValue("fileName", stringValue(metadata.get("fileName")))
                .addValue("fileType", stringValue(metadata.get("contentType")))
                .addValue("fileSize", longValue(metadata.get("contentLength")))
                .addValue("contentText", contentText)
                .addValue("metadata", toJson(metadata)));
    }

    @Override
    @Transactional
    public void replaceChunks(String sourceId, List<DocumentChunk> chunks) {
        jdbcTemplate.update("delete from public.paper_document_chunk where source_id = :sourceId",
                new MapSqlParameterSource("sourceId", sourceId));
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = chunks.stream()
                .map(chunk -> new MapSqlParameterSource()
                        .addValue("chunkId", chunk.chunkId())
                        .addValue("sourceId", chunk.sourceId())
                        .addValue("chunkIndex", chunk.chunkIndex())
                        .addValue("content", chunk.content())
                        .addValue("contentHash", sha256(chunk.content()))
                        .addValue("chunkStart", intValue(chunk.metadata(), "chunkStart"))
                        .addValue("chunkEnd", intValue(chunk.metadata(), "chunkEnd"))
                        .addValue("pageNumber", intValue(chunk.metadata(), "pageNumber"))
                        .addValue("sectionTitle", stringValue(chunk.metadata() == null ? null : chunk.metadata().get("sectionTitle")))
                        .addValue("metadata", toJson(chunk.metadata() == null ? Map.of() : chunk.metadata())))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                insert into public.paper_document_chunk (
                    chunk_id, source_id, chunk_index, content, content_hash, chunk_start, chunk_end, page_number, section_title, metadata
                ) values (
                    :chunkId, :sourceId, :chunkIndex, :content, :contentHash, :chunkStart, :chunkEnd, :pageNumber, :sectionTitle, cast(:metadata as jsonb)
                )
                """, batch);
    }

    @Override
    public void markIndexed(String sourceId, int chunkCount) {
        jdbcTemplate.update("""
                update public.paper_document
                set status = 'INDEXED', chunk_count = :chunkCount, error_message = null, updated_at = now()
                where source_id = :sourceId
                """, new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("chunkCount", chunkCount));
    }

    @Override
    public void markFailed(String sourceId, String errorMessage) {
        jdbcTemplate.update("""
                update public.paper_document
                set status = 'FAILED', error_message = :errorMessage, updated_at = now()
                where source_id = :sourceId
                """, new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("errorMessage", cut(errorMessage, 4000)));
    }

    @Override
    @Transactional
    public void markDeleted(String sourceId) {
        jdbcTemplate.update("delete from public.paper_document_chunk where source_id = :sourceId",
                new MapSqlParameterSource("sourceId", sourceId));
        jdbcTemplate.update("""
                update public.paper_document
                set status = 'DELETED', deleted_at = now(), updated_at = now()
                where source_id = :sourceId
                """, new MapSqlParameterSource("sourceId", sourceId));
    }

    private String documentWhereSql(String keyword, String status) {
        StringBuilder builder = new StringBuilder(" where 1 = 1\n");
        if (status == null || status.isBlank()) {
            builder.append(" and status <> 'DELETED'\n");
        } else if (!"ALL".equalsIgnoreCase(status)) {
            builder.append(" and status = :status\n");
        }
        if (keyword != null && !keyword.isBlank()) {
            builder.append("""
                     and (
                         title ilike :keyword
                         or source_id ilike :keyword
                         or file_name ilike :keyword
                         or doi ilike :keyword
                     )
                    """);
        }
        return builder.toString();
    }

    private String normalizeLikeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim() + "%";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    private Map<String, Object> jsonMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private Object jsonValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), Object.class);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("元数据 JSON 序列化失败", ex);
        }
    }

    private String toNullableJson(Object value) {
        if (value == null) {
            return null;
        }
        return toJson(value);
    }

    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intValue(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String cut(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName, OffsetDateTime.class);
    }

    private class DocumentSummaryRowMapper implements RowMapper<DocumentSummary> {
        @Override
        public DocumentSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DocumentSummary(
                    rs.getString("source_id"),
                    rs.getString("title"),
                    rs.getString("origin"),
                    rs.getString("file_name"),
                    rs.getString("file_type"),
                    rs.getObject("file_size", Long.class),
                    rs.getString("status"),
                    rs.getInt("chunk_count"),
                    rs.getObject("publish_year", Integer.class),
                    offsetDateTime(rs, "created_at"),
                    offsetDateTime(rs, "updated_at")
            );
        }
    }

    private class DocumentDetailRowMapper implements RowMapper<DocumentDetail> {
        @Override
        public DocumentDetail mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DocumentDetail(
                    rs.getString("source_id"),
                    rs.getString("title"),
                    rs.getString("origin"),
                    rs.getString("file_name"),
                    rs.getString("file_type"),
                    rs.getObject("file_size", Long.class),
                    jsonValue(rs.getObject("authors")),
                    rs.getString("abstract"),
                    rs.getString("doi"),
                    rs.getString("journal"),
                    rs.getObject("publish_year", Integer.class),
                    jsonValue(rs.getObject("keywords")),
                    rs.getString("content_text"),
                    jsonMap(rs.getObject("metadata")),
                    rs.getString("status"),
                    rs.getInt("chunk_count"),
                    rs.getString("error_message"),
                    offsetDateTime(rs, "created_at"),
                    offsetDateTime(rs, "updated_at"),
                    offsetDateTime(rs, "deleted_at")
            );
        }
    }

    private class DocumentChunkViewRowMapper implements RowMapper<DocumentChunkView> {
        @Override
        public DocumentChunkView mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DocumentChunkView(
                    rs.getString("chunk_id"),
                    rs.getInt("chunk_index"),
                    rs.getString("content"),
                    rs.getString("content_hash"),
                    rs.getObject("chunk_start", Integer.class),
                    rs.getObject("chunk_end", Integer.class),
                    rs.getObject("page_number", Integer.class),
                    rs.getString("section_title"),
                    jsonMap(rs.getObject("metadata")),
                    rs.getObject("vector_store_id", UUID.class),
                    offsetDateTime(rs, "created_at"),
                    offsetDateTime(rs, "updated_at")
            );
        }
    }
}