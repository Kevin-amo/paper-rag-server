package com.lqr.paperragserver.paper.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.common.model.DocumentAsset;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.paper.entity.PaperDocumentAssetEntity;
import com.lqr.paperragserver.paper.entity.PaperDocumentChunkEntity;
import com.lqr.paperragserver.paper.entity.PaperDocumentEntity;
import com.lqr.paperragserver.paper.mapper.PaperDocumentAssetMapper;
import com.lqr.paperragserver.paper.mapper.PaperDocumentChunkMapper;
import com.lqr.paperragserver.paper.mapper.PaperDocumentMapper;
import com.lqr.paperragserver.paper.service.PaperDocumentPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis-Plus 的文档元数据持久化服务。
 */
@Service
@RequiredArgsConstructor
public class PaperDocumentPersistenceServiceImpl implements PaperDocumentPersistenceService {

    private static final int SECTION_TITLE_MAX_LENGTH = 512;
    private static final Pattern SEARCH_TOKEN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]+");

    private final PaperDocumentMapper documentMapper;
    private final PaperDocumentChunkMapper chunkMapper;
    private final PaperDocumentAssetMapper assetMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询文档摘要，支持关键词和状态过滤。
     */
    @Override
    public PageResult<DocumentSummary> listDocuments(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, 1, 100);
        LambdaQueryWrapper<PaperDocumentEntity> wrapper = documentListWrapper(keyword, status)
                .orderByDesc(PaperDocumentEntity::getUpdatedAt)
                .orderByDesc(PaperDocumentEntity::getCreatedAt);
        Page<PaperDocumentEntity> result = documentMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
        List<DocumentSummary> items = result.getRecords().stream()
                .map(this::toDocumentSummary)
                .toList();
        return new PageResult<>(items, safePage, safeSize, result.getTotal());
    }

    /**
     * 按来源 ID 查询文档详情。
     */
    @Override
    public Optional<DocumentDetail> findDocument(String sourceId) {
        return Optional.ofNullable(documentMapper.selectOne(new LambdaQueryWrapper<PaperDocumentEntity>()
                        .eq(PaperDocumentEntity::getSourceId, sourceId)))
                .map(this::toDocumentDetail);
    }

    /**
     * 分页查询指定文档的分块视图。
     */
    @Override
    public PageResult<DocumentChunkView> listChunks(String sourceId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, 1, 200);
        Page<PaperDocumentChunkEntity> result = chunkMapper.selectPage(new Page<>(safePage + 1L, safeSize),
                new LambdaQueryWrapper<PaperDocumentChunkEntity>()
                        .eq(PaperDocumentChunkEntity::getSourceId, sourceId)
                        .orderByAsc(PaperDocumentChunkEntity::getChunkIndex));
        List<DocumentChunkView> items = result.getRecords().stream()
                .map(this::toDocumentChunkView)
                .toList();
        return new PageResult<>(items, safePage, safeSize, result.getTotal());
    }

    /**
     * 基于本地关键词评分检索候选文档分块。
     */
    @Override
    public List<DocumentChunk> searchChunks(String question, int limit) {
        if (question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }
        int safeLimit = clamp(limit, 1, 200);
        List<DocumentChunk> candidates = chunkMapper.selectSearchCandidates().stream()
                .map(entity -> new DocumentChunk(
                        entity.getChunkId(),
                        entity.getSourceId(),
                        intOrZero(entity.getChunkIndex()),
                        entity.getContent(),
                        safeMetadata(entity.getMetadata())
                ))
                .toList();
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

    /**
     * 更新文档可编辑元数据字段。
     */
    @Override
    public void updateMetadata(String sourceId, DocumentMetadataUpdate update) {
        Map<String, Object> metadata = update.metadata() == null ? Map.of() : update.metadata();
        documentMapper.updateMetadata(
                sourceId,
                blankToNull(update.title()),
                toNullableJson(update.authors()),
                blankToNull(update.abstractText()),
                blankToNull(update.doi()),
                blankToNull(update.journal()),
                update.publishYear(),
                toNullableJson(update.keywords()),
                toJson(metadata)
        );
    }

    /**
     * 恢复已软删除文档。
     */
    @Override
    public void restore(String sourceId) {
        documentMapper.restore(sourceId);
    }

    /**
     * 标记文档进入解析中状态并保存正文与基础元数据。
     */
    @Override
    @Transactional
    public void markParsing(DocumentSource source, String contentText) {
        Map<String, Object> metadata = source.metadata() == null ? Map.of() : source.metadata();
        documentMapper.upsertParsing(
                source.sourceId(),
                nonBlank(source.title(), source.origin(), source.sourceId()),
                source.origin(),
                stringValue(metadata.get(MetadataKeys.FILE_NAME)),
                stringValue(metadata.get(MetadataKeys.CONTENT_TYPE)),
                longValue(metadata.get(MetadataKeys.CONTENT_LENGTH)),
                contentText,
                toJson(metadata)
        );
    }

    /**
     * 替换指定文档的资产记录。
     */
    @Override
    @Transactional
    public void replaceAssets(String sourceId, List<DocumentAsset> assets) {
        assetMapper.delete(new LambdaQueryWrapper<PaperDocumentAssetEntity>()
                .eq(PaperDocumentAssetEntity::getSourceId, sourceId));
        if (assets == null || assets.isEmpty()) {
            return;
        }
        for (DocumentAsset asset : assets) {
            assetMapper.insert(toAssetEntity(asset));
        }
    }

    /**
     * 查询指定文档的资产视图列表。
     */
    @Override
    public List<DocumentAssetView> listAssets(String sourceId, List<String> assetIds) {
        LambdaQueryWrapper<PaperDocumentAssetEntity> wrapper = new LambdaQueryWrapper<PaperDocumentAssetEntity>()
                .select(PaperDocumentAssetEntity::getAssetId,
                        PaperDocumentAssetEntity::getSourceId,
                        PaperDocumentAssetEntity::getAssetIndex,
                        PaperDocumentAssetEntity::getAssetType,
                        PaperDocumentAssetEntity::getFileName,
                        PaperDocumentAssetEntity::getContentType,
                        PaperDocumentAssetEntity::getFileSize,
                        PaperDocumentAssetEntity::getContentHash,
                        PaperDocumentAssetEntity::getExtractedText,
                        PaperDocumentAssetEntity::getTextStart,
                        PaperDocumentAssetEntity::getTextEnd,
                        PaperDocumentAssetEntity::getMetadata,
                        PaperDocumentAssetEntity::getCreatedAt,
                        PaperDocumentAssetEntity::getUpdatedAt)
                .eq(PaperDocumentAssetEntity::getSourceId, sourceId)
                .orderByAsc(PaperDocumentAssetEntity::getAssetIndex);
        if (assetIds != null && !assetIds.isEmpty()) {
            wrapper.in(PaperDocumentAssetEntity::getAssetId, assetIds);
        }
        return assetMapper.selectList(wrapper).stream()
                .map(this::toDocumentAssetView)
                .toList();
    }

    /**
     * 查询指定文档下的单个资产视图。
     */
    @Override
    public Optional<DocumentAssetView> findAsset(String sourceId, String assetId) {
        return Optional.ofNullable(assetMapper.selectOne(new LambdaQueryWrapper<PaperDocumentAssetEntity>()
                        .eq(PaperDocumentAssetEntity::getSourceId, sourceId)
                        .eq(PaperDocumentAssetEntity::getAssetId, assetId)))
                .map(this::toDocumentAssetView);
    }

    /**
     * 替换指定文档的分块记录。
     */
    @Override
    @Transactional
    public void replaceChunks(String sourceId, List<DocumentChunk> chunks) {
        chunkMapper.delete(new LambdaQueryWrapper<PaperDocumentChunkEntity>()
                .eq(PaperDocumentChunkEntity::getSourceId, sourceId));
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (DocumentChunk chunk : chunks) {
            chunkMapper.insert(toChunkEntity(chunk));
        }
    }

    /**
     * 标记文档索引完成并记录分块数量。
     */
    @Override
    public void markIndexed(String sourceId, int chunkCount) {
        documentMapper.markIndexed(sourceId, chunkCount);
    }

    /**
     * 标记文档处理失败并截断过长错误信息。
     */
    @Override
    public void markFailed(String sourceId, String errorMessage) {
        documentMapper.markFailed(sourceId, cut(errorMessage, 4000));
    }

    /**
     * 软删除文档并清理关联资产和分块。
     */
    @Override
    @Transactional
    public void markDeleted(String sourceId) {
        assetMapper.delete(new LambdaQueryWrapper<PaperDocumentAssetEntity>()
                .eq(PaperDocumentAssetEntity::getSourceId, sourceId));
        chunkMapper.delete(new LambdaQueryWrapper<PaperDocumentChunkEntity>()
                .eq(PaperDocumentChunkEntity::getSourceId, sourceId));
        documentMapper.markDeleted(sourceId);
    }

    /**
     * 构建文档列表查询条件。
     */
    private LambdaQueryWrapper<PaperDocumentEntity> documentListWrapper(String keyword, String status) {
        LambdaQueryWrapper<PaperDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        if (status == null || status.isBlank()) {
            wrapper.ne(PaperDocumentEntity::getStatus, "DELETED")
                    .isNull(PaperDocumentEntity::getDeletedAt);
        } else if (!"ALL".equalsIgnoreCase(status)) {
            String normalizedStatus = normalizeStatus(status);
            wrapper.eq(PaperDocumentEntity::getStatus, normalizedStatus);
            if (!"DELETED".equalsIgnoreCase(normalizedStatus)) {
                wrapper.isNull(PaperDocumentEntity::getDeletedAt);
            }
        }
        String normalizedKeyword = normalizeLikeKeyword(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query.apply("title ilike {0}", normalizedKeyword)
                    .or().apply("source_id ilike {0}", normalizedKeyword)
                    .or().apply("file_name ilike {0}", normalizedKeyword)
                    .or().apply("doi ilike {0}", normalizedKeyword));
        }
        return wrapper;
    }

    /**
     * 将文档实体转换为摘要视图。
     */
    private DocumentSummary toDocumentSummary(PaperDocumentEntity entity) {
        return new DocumentSummary(
                entity.getSourceId(),
                entity.getTitle(),
                entity.getOrigin(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getStatus(),
                intOrZero(entity.getChunkCount()),
                entity.getPublishYear(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 将文档实体转换为详情视图。
     */
    private DocumentDetail toDocumentDetail(PaperDocumentEntity entity) {
        return new DocumentDetail(
                entity.getSourceId(),
                entity.getTitle(),
                entity.getOrigin(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getAuthors(),
                entity.getAbstractText(),
                entity.getDoi(),
                entity.getJournal(),
                entity.getPublishYear(),
                entity.getKeywords(),
                entity.getContentText(),
                safeMetadata(entity.getMetadata()),
                entity.getStatus(),
                intOrZero(entity.getChunkCount()),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }

    /**
     * 将分块实体转换为分块视图。
     */
    private DocumentChunkView toDocumentChunkView(PaperDocumentChunkEntity entity) {
        return new DocumentChunkView(
                entity.getChunkId(),
                intOrZero(entity.getChunkIndex()),
                entity.getContent(),
                entity.getContentHash(),
                entity.getChunkStart(),
                entity.getChunkEnd(),
                entity.getPageNumber(),
                entity.getSectionTitle(),
                safeMetadata(entity.getMetadata()),
                entity.getVectorStoreId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 将资产实体转换为资产视图。
     */
    private DocumentAssetView toDocumentAssetView(PaperDocumentAssetEntity entity) {
        return new DocumentAssetView(
                entity.getAssetId(),
                entity.getSourceId(),
                intOrZero(entity.getAssetIndex()),
                entity.getAssetType(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getContentHash(),
                entity.getContent(),
                entity.getExtractedText(),
                entity.getTextStart(),
                entity.getTextEnd(),
                safeMetadata(entity.getMetadata()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 将文档分块领域对象转换为分块实体。
     */
    private PaperDocumentChunkEntity toChunkEntity(DocumentChunk chunk) {
        Map<String, Object> metadata = chunk.metadata() == null ? Map.of() : chunk.metadata();
        PaperDocumentChunkEntity entity = new PaperDocumentChunkEntity();
        entity.setChunkId(chunk.chunkId());
        entity.setSourceId(chunk.sourceId());
        entity.setChunkIndex(chunk.chunkIndex());
        entity.setContent(chunk.content());
        entity.setContentHash(sha256(chunk.content()));
        entity.setChunkStart(intValue(metadata, MetadataKeys.CHUNK_START));
        entity.setChunkEnd(intValue(metadata, MetadataKeys.CHUNK_END));
        entity.setPageNumber(intValue(metadata, MetadataKeys.PAGE_NUMBER));
        entity.setSectionTitle(cut(stringValue(metadata.get(MetadataKeys.SECTION_TITLE)), SECTION_TITLE_MAX_LENGTH));
        entity.setMetadata(metadata);
        return entity;
    }

    /**
     * 将文档资产领域对象转换为资产实体。
     */
    private PaperDocumentAssetEntity toAssetEntity(DocumentAsset asset) {
        PaperDocumentAssetEntity entity = new PaperDocumentAssetEntity();
        entity.setAssetId(asset.assetId());
        entity.setSourceId(asset.sourceId());
        entity.setAssetIndex(asset.assetIndex());
        entity.setAssetType(asset.assetType());
        entity.setFileName(asset.fileName());
        entity.setContentType(asset.contentType());
        entity.setFileSize(asset.fileSize());
        entity.setContentHash(asset.contentHash());
        entity.setContent(asset.content());
        entity.setExtractedText(asset.extractedText());
        entity.setTextStart(asset.textStart());
        entity.setTextEnd(asset.textEnd());
        entity.setMetadata(asset.metadata() == null ? Map.of() : asset.metadata());
        return entity;
    }

    /**
     * 将空元数据统一回退为空映射。
     */
    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : metadata;
    }

    /**
     * 将查询关键词转换为 SQL LIKE 模式。
     */
    private String normalizeLikeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim() + "%";
    }

    /**
     * 规范化文档状态过滤值。
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return status.trim().toUpperCase();
    }

    /**
     * 将对象序列化为 JSON 字符串，空值回退为空对象。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("元数据 JSON 序列化失败", ex);
        }
    }

    /**
     * 将非空对象序列化为 JSON 字符串。
     */
    private String toNullableJson(Object value) {
        if (value == null) {
            return null;
        }
        return toJson(value);
    }

    /**
     * 从候选字符串中返回第一个非空值。
     */
    private String nonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    /**
     * 将对象转换为字符串表示。
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将空白字符串转换为 null。
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 将元数据值安全转换为长整数。
     */
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

    /**
     * 从元数据映射中安全读取整数值。
     */
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

    /**
     * 将可空整数转换为非空整数。
     */
    private int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 从用户问题中提取用于本地关键词检索的词项。
     */
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

    /**
     * 将检索词及其 n-gram 片段加入词项集合。
     */
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

    /**
     * 根据问题词项与分块内容命中情况计算本地检索分数。
     */
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

    /**
     * 将元数据值拼接为可参与关键词检索的文本。
     */
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

    /**
     * 规范化用于关键词检索的文本。
     */
    private String normalizeSearchText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * 将整数限制在指定闭区间内。
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 截断超出最大长度的字符串。
     */
    private String cut(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 计算字符串内容的 SHA-256 十六进制摘要。
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    private record ScoredDocumentChunk(DocumentChunk chunk, double score) {
    }
}