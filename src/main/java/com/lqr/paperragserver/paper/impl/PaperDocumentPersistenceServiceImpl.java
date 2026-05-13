package com.lqr.paperragserver.paper.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.common.DocumentAsset;
import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentSource;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import lombok.RequiredArgsConstructor;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * JDBC 实现的文档元数据持久化服务。
 */
@Service
@RequiredArgsConstructor
public class PaperDocumentPersistenceServiceImpl implements PaperDocumentPersistenceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int SECTION_TITLE_MAX_LENGTH = 512;
    private static final Pattern SEARCH_TOKEN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]+");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

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
    public List<DocumentChunk> searchChunks(String question, int limit) {
        if (question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }
        int safeLimit = clamp(limit, 1, 200);
        List<DocumentChunk> candidates = jdbcTemplate.query("""
                select c.chunk_id, c.source_id, c.chunk_index, c.content, c.metadata
                from public.paper_document_chunk c
                join public.paper_document d on d.source_id = c.source_id
                where d.deleted_at is null
                  and d.status = 'INDEXED'
                order by c.source_id asc, c.chunk_index asc
                """, new MapSqlParameterSource(), (rs, rowNum) -> new DocumentChunk(
                rs.getString("chunk_id"),
                rs.getString("source_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                jsonMap(rs.getObject("metadata"))
        ));

        List<String> terms = extractSearchTerms(question);
        return candidates.stream()
                .map(chunk -> new ScoredDocumentChunk(chunk, lexicalScore(question, chunk, terms)))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredDocumentChunk::score).reversed()
                        .thenComparing(hit -> hit.chunk().sourceId())
                        .thenComparingInt(hit -> hit.chunk().chunkIndex()))
                .limit(safeLimit)
                .map(ScoredDocumentChunk::chunk)
                .toList();
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
                where source_id = :sourceId and deleted_at is not null
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
    public void replaceAssets(String sourceId, List<DocumentAsset> assets) {
        jdbcTemplate.update("delete from public.paper_document_asset where source_id = :sourceId",
                new MapSqlParameterSource("sourceId", sourceId));
        if (assets == null || assets.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = assets.stream()
                .map(asset -> new MapSqlParameterSource()
                        .addValue("assetId", asset.assetId())
                        .addValue("sourceId", asset.sourceId())
                        .addValue("assetIndex", asset.assetIndex())
                        .addValue("assetType", asset.assetType())
                        .addValue("fileName", asset.fileName())
                        .addValue("contentType", asset.contentType())
                        .addValue("fileSize", asset.fileSize())
                        .addValue("contentHash", asset.contentHash())
                        .addValue("content", asset.content())
                        .addValue("extractedText", asset.extractedText())
                        .addValue("textStart", asset.textStart())
                        .addValue("textEnd", asset.textEnd())
                        .addValue("metadata", toJson(asset.metadata() == null ? Map.of() : asset.metadata())))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate("""
                insert into public.paper_document_asset (
                    asset_id, source_id, asset_index, asset_type, file_name, content_type, file_size,
                    content_hash, content, extracted_text, text_start, text_end, metadata
                ) values (
                    :assetId, :sourceId, :assetIndex, :assetType, :fileName, :contentType, :fileSize,
                    :contentHash, :content, :extractedText, :textStart, :textEnd, cast(:metadata as jsonb)
                )
                """, batch);
    }

    @Override
    public List<DocumentAssetView> listAssets(String sourceId, List<String> assetIds) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("assetIds", assetIds == null || assetIds.isEmpty() ? null : assetIds);
        String assetFilter = assetIds == null || assetIds.isEmpty() ? "" : " and asset_id in (:assetIds)\n";
        return jdbcTemplate.query("""
                select asset_id, source_id, asset_index, asset_type, file_name, content_type, file_size,
                       content_hash, null::bytea as content, extracted_text, text_start, text_end,
                       metadata, created_at, updated_at
                from public.paper_document_asset
                where source_id = :sourceId
                """ + assetFilter + """
                order by asset_index asc
                """, params, new DocumentAssetViewRowMapper());
    }

    @Override
    public Optional<DocumentAssetView> findAsset(String sourceId, String assetId) {
        List<DocumentAssetView> results = jdbcTemplate.query("""
                select asset_id, source_id, asset_index, asset_type, file_name, content_type, file_size,
                       content_hash, content, extracted_text, text_start, text_end,
                       metadata, created_at, updated_at
                from public.paper_document_asset
                where source_id = :sourceId and asset_id = :assetId
                """, new MapSqlParameterSource()
                .addValue("sourceId", sourceId)
                .addValue("assetId", assetId), new DocumentAssetViewRowMapper());
        return results.stream().findFirst();
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
                        .addValue("sectionTitle", cut(stringValue(chunk.metadata() == null ? null : chunk.metadata().get("sectionTitle")), SECTION_TITLE_MAX_LENGTH))
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
                  and deleted_at is null
                  and status <> 'DELETED'
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
        jdbcTemplate.update("delete from public.paper_document_asset where source_id = :sourceId",
                new MapSqlParameterSource("sourceId", sourceId));
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
            builder.append(" and deleted_at is null\n");
        } else if (!"ALL".equalsIgnoreCase(status)) {
            builder.append(" and status = :status\n");
            if (!"DELETED".equalsIgnoreCase(status.trim())) {
                builder.append(" and deleted_at is null\n");
            }
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

    private List<String> extractSearchTerms(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Matcher matcher = SEARCH_TOKEN.matcher(question);
        while (matcher.find()) {
            addSearchTerms(terms, normalizeSearchText(matcher.group()));
        }
        return List.copyOf(terms);
    }

    private void addSearchTerms(LinkedHashSet<String> terms, String token) {
        if (token == null || token.length() < 2) {
            return;
        }
        if (token.length() <= 4) {
            terms.add(token);
            return;
        }
        int maxGram = Math.min(4, token.length());
        for (int size = maxGram; size >= 2; size--) {
            for (int start = 0; start + size <= token.length(); start++) {
                terms.add(token.substring(start, start + size));
            }
        }
    }

    private double lexicalScore(String question, DocumentChunk chunk, List<String> terms) {
        String haystack = normalizeSearchText(chunk.content()) + "|" + metadataText(chunk.metadata());
        double score = 0;
        String normalizedQuestion = normalizeSearchText(question);
        if (!normalizedQuestion.isBlank() && haystack.contains(normalizedQuestion)) {
            score += 3.0;
        }
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += 1.0 + (term.length() * 0.1);
            }
        }
        return score;
    }

    private String metadataText(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        return metadata.values().stream()
                .map(this::stringValue)
                .filter(Objects::nonNull)
                .map(this::normalizeSearchText)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("|"));
    }

    private String normalizeSearchText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
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

    private record ScoredDocumentChunk(DocumentChunk chunk, double score) {
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

    private class DocumentAssetViewRowMapper implements RowMapper<DocumentAssetView> {
        @Override
        public DocumentAssetView mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DocumentAssetView(
                    rs.getString("asset_id"),
                    rs.getString("source_id"),
                    rs.getInt("asset_index"),
                    rs.getString("asset_type"),
                    rs.getString("file_name"),
                    rs.getString("content_type"),
                    rs.getObject("file_size", Long.class),
                    rs.getString("content_hash"),
                    rs.getBytes("content"),
                    rs.getString("extracted_text"),
                    rs.getObject("text_start", Integer.class),
                    rs.getObject("text_end", Integer.class),
                    jsonMap(rs.getObject("metadata")),
                    offsetDateTime(rs, "created_at"),
                    offsetDateTime(rs, "updated_at")
            );
        }
    }
}