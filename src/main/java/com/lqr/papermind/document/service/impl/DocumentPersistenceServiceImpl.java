package com.lqr.papermind.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.common.model.DocumentAsset;
import com.lqr.papermind.common.model.DocumentChunk;
import com.lqr.papermind.common.model.DocumentSource;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.entity.DocumentAssetEntity;
import com.lqr.papermind.document.entity.DocumentChunkEntity;
import com.lqr.papermind.document.entity.DocumentEntity;
import com.lqr.papermind.document.mapper.DocumentAssetMapper;
import com.lqr.papermind.document.mapper.DocumentChunkMapper;
import com.lqr.papermind.document.mapper.DocumentMapper;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
 * 基于 MyBatis-Plus 的文档元数据持久化服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPersistenceServiceImpl implements DocumentPersistenceService {

    private static final int SECTION_TITLE_MAX_LENGTH = 512;
    private static final Pattern SEARCH_TOKEN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]+");

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final DocumentAssetMapper assetMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询文档摘要，支持关键词和状态过滤。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param keyword 关键词过滤条件
     * @param status 状态过滤条件
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 分页查询结果
     */
    @Override
    public PageResult<DocumentSummary> listDocuments(UUID ownerUserId, String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, 1, 100);
        LambdaQueryWrapper<DocumentEntity> wrapper = documentListWrapper(ownerUserId, keyword, status)
                .orderByDesc(DocumentEntity::getUpdatedAt)
                .orderByDesc(DocumentEntity::getCreatedAt);
        Page<DocumentEntity> result = documentMapper.selectPage(new Page<>(safePage + 1L, safeSize), wrapper);
        List<DocumentSummary> items = result.getRecords().stream()
                .map(this::toDocumentSummary)
                .toList();
        return new PageResult<>(items, safePage, safeSize, result.getTotal());
    }

    /**
     * 按来源 ID 查询文档详情。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @return 文档详情，不存在时返回空
     */
    @Override
    public Optional<DocumentDetail> findDocument(UUID ownerUserId, String sourceId) {
        return findDocument(ownerUserId, sourceId, MetadataKeys.SOURCE_TYPE_USER);
    }

    @Override
    public Optional<DocumentDetail> findAnyDocument(UUID ownerUserId, String sourceId) {
        return findDocument(ownerUserId, sourceId, null);
    }

    @Override
    public Optional<DocumentDetail> findReviewDocument(UUID ownerUserId, String sourceId) {
        return findDocument(ownerUserId, sourceId, MetadataKeys.SOURCE_TYPE_REVIEW);
    }

    /**
     * 按来源 ID 和来源类型查询文档详情。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param sourceType 来源类型过滤条件，为 null 时不过滤
     * @return 文档详情，不存在时返回空
     */
    private Optional<DocumentDetail> findDocument(UUID ownerUserId, String sourceId, String sourceType) {
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentEntity::getSourceId, sourceId);
        applySourceTypeFilter(wrapper, sourceType);
        return Optional.ofNullable(documentMapper.selectOne(wrapper))
                .map(this::toDocumentDetail);
    }

    /**
     * 根据 sourceId 列表批量查询已索引文档状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceIds 文档来源标识列表
     * @return sourceId 到是否已索引的映射
     */
    @Override
    public Map<String, Boolean> findIndexedDocuments(UUID ownerUserId, List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<DocumentEntity>()
                .select(DocumentEntity::getSourceId, DocumentEntity::getStatus, DocumentEntity::getDeletedAt)
                .eq(DocumentEntity::getOwnerUserId, ownerUserId)
                .in(DocumentEntity::getSourceId, sourceIds)
                .isNull(DocumentEntity::getDeletedAt)
                .eq(DocumentEntity::getStatus, "INDEXED");
        applySourceTypeFilter(wrapper, MetadataKeys.SOURCE_TYPE_USER);
        List<DocumentEntity> entities = documentMapper.selectList(wrapper);
        return entities.stream()
                .collect(Collectors.toMap(DocumentEntity::getSourceId, e -> Boolean.TRUE));
    }

    /**
     * 分页查询指定文档的分块视图。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 分页查询结果
     */
    @Override
    public PageResult<DocumentChunkView> listChunks(UUID ownerUserId, String sourceId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, 1, 200);
        if (findDocument(ownerUserId, sourceId).isEmpty()) {
            return new PageResult<>(List.of(), safePage, safeSize, 0);
        }
        Page<DocumentChunkEntity> result = chunkMapper.selectPage(new Page<>(safePage + 1L, safeSize),
                new LambdaQueryWrapper<DocumentChunkEntity>()
                        .eq(DocumentChunkEntity::getOwnerUserId, ownerUserId)
                        .eq(DocumentChunkEntity::getSourceId, sourceId)
                        .orderByAsc(DocumentChunkEntity::getChunkIndex));
        List<DocumentChunkView> items = result.getRecords().stream()
                .map(this::toDocumentChunkView)
                .toList();
        return new PageResult<>(items, safePage, safeSize, result.getTotal());
    }

    /**
     * 基于本地关键词评分检索候选文档分块。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param question 检索问题
     * @param limit 最大返回条数
     * @return 匹配的文档分块列表
     */
    @Override
    public List<DocumentChunk> searchChunks(UUID ownerUserId, String question, int limit) {
        if (question == null || question.isBlank() || limit <= 0) {
            return List.of();
        }
        int safeLimit = clamp(limit, 1, 200);
        List<DocumentChunk> candidates = chunkMapper.selectSearchCandidates(ownerUserId).stream()
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
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param update 元数据更新内容
     */
    @Override
    public void updateMetadata(UUID ownerUserId, String sourceId, DocumentMetadataUpdate update) {
        if (findDocument(ownerUserId, sourceId).isEmpty()) {
            return;
        }
        Map<String, Object> metadata = userMetadataUpdate(update.metadata());
        documentMapper.updateMetadata(
                ownerUserId,
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
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     */
    @Override
    public void restore(UUID ownerUserId, String sourceId) {
        if (findDocument(ownerUserId, sourceId).isEmpty()) {
            return;
        }
        documentMapper.restore(ownerUserId, sourceId);
    }

    /**
     * 标记文档进入指定处理状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param status 目标状态
     * @param progress 进度百分比
     */
    @Override
    public void markStatus(UUID ownerUserId, String sourceId, String status, int progress) {
        documentMapper.markStatus(ownerUserId, sourceId, status, clamp(progress, 0, 100));
    }

    /**
     * 标记文档进入解析中状态并保存正文与基础元数据。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param source 文档来源信息
     * @param contentText 文档正文文本
     */
    @Override
    @Transactional
    public void markParsing(UUID ownerUserId, DocumentSource source, String contentText) {
        Map<String, Object> metadata = ownerMetadata(ownerUserId, source.metadata());
        metadata.putIfAbsent(MetadataKeys.SOURCE_TYPE, MetadataKeys.SOURCE_TYPE_USER);
        documentMapper.upsertParsing(
                ownerUserId,
                source.sourceId(),
                nonBlank(source.title(), source.origin(), source.sourceId()),
                source.origin(),
                stringValue(metadata.get(MetadataKeys.FILE_NAME)),
                stringValue(metadata.get(MetadataKeys.CONTENT_TYPE)),
                longValue(metadata.get(MetadataKeys.CONTENT_LENGTH)),
                toNullableJson(metadata.get(MetadataKeys.AUTHORS)),
                blankToNull(stringValue(metadata.get(MetadataKeys.ABSTRACT_TEXT))),
                blankToNull(stringValue(metadata.get(MetadataKeys.DOI))),
                blankToNull(stringValue(metadata.get(MetadataKeys.JOURNAL))),
                intValue(metadata, MetadataKeys.PUBLISH_YEAR),
                toNullableJson(metadata.get(MetadataKeys.KEYWORDS)),
                contentText,
                toJson(metadata)
        );
        log.info("document.persistence.status ownerUserId={} sourceId={} status=PARSING progress={} fileName={} fileType={} fileSize={} contentLength={}",
                ownerUserId,
                source.sourceId(),
                30,
                stringValue(metadata.get(MetadataKeys.FILE_NAME)),
                stringValue(metadata.get(MetadataKeys.CONTENT_TYPE)),
                longValue(metadata.get(MetadataKeys.CONTENT_LENGTH)),
                contentText == null ? 0 : contentText.length());
    }

    /**
     * 替换指定文档的资产记录。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param assets 新的资产列表
     */
    @Override
    @Transactional
    public void replaceAssets(UUID ownerUserId, String sourceId, List<DocumentAsset> assets) {
        assetMapper.delete(new LambdaQueryWrapper<DocumentAssetEntity>()
                .eq(DocumentAssetEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentAssetEntity::getSourceId, sourceId));
        int assetCount = assets == null ? 0 : assets.size();
        if (assets == null || assets.isEmpty()) {
            log.info("document.persistence.assets.replace ownerUserId={} sourceId={} assetCount={}", ownerUserId, sourceId, assetCount);
            return;
        }
        for (DocumentAsset asset : assets) {
            assetMapper.insert(toAssetEntity(ownerUserId, asset));
        }
        log.info("document.persistence.assets.replace ownerUserId={} sourceId={} assetCount={}", ownerUserId, sourceId, assetCount);
    }

    /**
     * 查询指定文档的资产视图列表。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param assetIds 需要过滤的资产 ID 列表，为空时不做过滤
     * @return 资产视图列表
     */
    @Override
    public List<DocumentAssetView> listAssets(UUID ownerUserId, String sourceId, List<String> assetIds) {
        if (findDocument(ownerUserId, sourceId).isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DocumentAssetEntity> wrapper = new LambdaQueryWrapper<DocumentAssetEntity>()
                .select(DocumentAssetEntity::getAssetId,
                        DocumentAssetEntity::getSourceId,
                        DocumentAssetEntity::getAssetIndex,
                        DocumentAssetEntity::getAssetType,
                        DocumentAssetEntity::getFileName,
                        DocumentAssetEntity::getContentType,
                        DocumentAssetEntity::getFileSize,
                        DocumentAssetEntity::getContentHash,
                        DocumentAssetEntity::getExtractedText,
                        DocumentAssetEntity::getTextStart,
                        DocumentAssetEntity::getTextEnd,
                        DocumentAssetEntity::getMetadata,
                        DocumentAssetEntity::getCreatedAt,
                        DocumentAssetEntity::getUpdatedAt)
                .eq(DocumentAssetEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentAssetEntity::getSourceId, sourceId)
                .orderByAsc(DocumentAssetEntity::getAssetIndex);
        if (assetIds != null && !assetIds.isEmpty()) {
            wrapper.in(DocumentAssetEntity::getAssetId, assetIds);
        }
        return assetMapper.selectList(wrapper).stream()
                .map(this::toDocumentAssetView)
                .toList();
    }

    /**
     * 查询指定文档下的单个资产视图。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param assetId 资产 ID
     * @return 资产视图，不存在时返回空
     */
    @Override
    public Optional<DocumentAssetView> findAsset(UUID ownerUserId, String sourceId, String assetId) {
        if (findDocument(ownerUserId, sourceId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(assetMapper.selectOne(new LambdaQueryWrapper<DocumentAssetEntity>()
                        .eq(DocumentAssetEntity::getOwnerUserId, ownerUserId)
                        .eq(DocumentAssetEntity::getSourceId, sourceId)
                        .eq(DocumentAssetEntity::getAssetId, assetId)))
                .map(this::toDocumentAssetView);
    }

    /**
     * 替换指定文档的分块记录。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param chunks 新的分块列表
     */
    @Override
    @Transactional
    public void replaceChunks(UUID ownerUserId, String sourceId, List<DocumentChunk> chunks) {
        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentChunkEntity::getSourceId, sourceId));
        int chunkCount = chunks == null ? 0 : chunks.size();
        if (chunks == null || chunks.isEmpty()) {
            log.info("document.persistence.chunks.replace ownerUserId={} sourceId={} chunkCount={}", ownerUserId, sourceId, chunkCount);
            return;
        }
        for (DocumentChunk chunk : chunks) {
            chunkMapper.insert(toChunkEntity(ownerUserId, chunk));
        }
        log.info("document.persistence.chunks.replace ownerUserId={} sourceId={} chunkCount={}", ownerUserId, sourceId, chunkCount);
    }

    /**
     * 标记文档索引完成并记录分块数量。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param chunkCount 分块数量
     */
    @Override
    public void markIndexed(UUID ownerUserId, String sourceId, int chunkCount) {
        documentMapper.markIndexed(ownerUserId, sourceId, chunkCount);
        log.info("document.persistence.status ownerUserId={} sourceId={} status=INDEXED progress={} chunkCount={}",
                ownerUserId, sourceId, 100, chunkCount);
    }

    /**
     * 标记文档处理失败并截断过长错误信息。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param errorMessage 错误信息
     */
    @Override
    public void markFailed(UUID ownerUserId, String sourceId, String errorMessage) {
        documentMapper.markFailed(ownerUserId, sourceId, cut(errorMessage, 4000));
        log.info("document.persistence.status ownerUserId={} sourceId={} status=FAILED progress={} errorMessageLength={}",
                ownerUserId, sourceId, 100, errorMessage == null ? 0 : errorMessage.length());
    }

    /**
     * 软删除文档并清理关联资产和分块。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     */
    @Override
    @Transactional
    public void markDeleted(UUID ownerUserId, String sourceId) {
        if (findDocument(ownerUserId, sourceId).isEmpty()) {
            return;
        }
        assetMapper.delete(new LambdaQueryWrapper<DocumentAssetEntity>()
                .eq(DocumentAssetEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentAssetEntity::getSourceId, sourceId));
        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getOwnerUserId, ownerUserId)
                .eq(DocumentChunkEntity::getSourceId, sourceId));
        documentMapper.markDeletedUserDocument(ownerUserId, sourceId);
    }

    /**
     * 软删除当前用户的全部文档并清理关联资产和分块。
     *
     * @param ownerUserId 文档所属用户 ID
     */
    @Override
    @Transactional
    public void markAllDeleted(UUID ownerUserId) {
        assetMapper.deleteUserDocumentAssets(ownerUserId);
        chunkMapper.deleteUserDocumentChunks(ownerUserId);
        documentMapper.markAllUserDocumentsDeleted(ownerUserId);
    }

    /**
     * 构建文档列表查询条件。
     */
    private LambdaQueryWrapper<DocumentEntity> documentListWrapper(UUID ownerUserId, String keyword, String status) {
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<DocumentEntity>()
                .eq(DocumentEntity::getOwnerUserId, ownerUserId);
        applySourceTypeFilter(wrapper, MetadataKeys.SOURCE_TYPE_USER);
        if (status == null || status.isBlank()) {
            wrapper.ne(DocumentEntity::getStatus, "DELETED")
                    .isNull(DocumentEntity::getDeletedAt);
        } else if (!"ALL".equalsIgnoreCase(status)) {
            String normalizedStatus = normalizeStatus(status);
            wrapper.eq(DocumentEntity::getStatus, normalizedStatus);
            if (!"DELETED".equalsIgnoreCase(normalizedStatus)) {
                wrapper.isNull(DocumentEntity::getDeletedAt);
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
    private DocumentSummary toDocumentSummary(DocumentEntity entity) {
        return new DocumentSummary(
                entity.getSourceId(),
                entity.getOwnerUserId(),
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
    private DocumentDetail toDocumentDetail(DocumentEntity entity) {
        return new DocumentDetail(
                entity.getSourceId(),
                entity.getOwnerUserId(),
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
    private DocumentChunkView toDocumentChunkView(DocumentChunkEntity entity) {
        return new DocumentChunkView(
                entity.getChunkId(),
                entity.getOwnerUserId(),
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
    private DocumentAssetView toDocumentAssetView(DocumentAssetEntity entity) {
        return new DocumentAssetView(
                entity.getAssetId(),
                entity.getSourceId(),
                entity.getOwnerUserId(),
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
    private DocumentChunkEntity toChunkEntity(UUID ownerUserId, DocumentChunk chunk) {
        Map<String, Object> metadata = ownerMetadata(ownerUserId, chunk.metadata());
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
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
    private DocumentAssetEntity toAssetEntity(UUID ownerUserId, DocumentAsset asset) {
        DocumentAssetEntity entity = new DocumentAssetEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
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
        entity.setMetadata(ownerMetadata(ownerUserId, asset.metadata()));
        return entity;
    }

    /**
     * 将空元数据统一回退为空映射。
     */
    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : metadata;
    }

    /**
     * 为查询条件应用来源类型过滤，用户类型兼容无来源类型的记录。
     *
     * @param wrapper 查询条件包装器
     * @param sourceType 来源类型，为 null 时不过滤
     */
    private void applySourceTypeFilter(LambdaQueryWrapper<DocumentEntity> wrapper, String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return;
        }
        if (MetadataKeys.SOURCE_TYPE_USER.equals(sourceType)) {
            wrapper.and(query -> query.apply("metadata ->> 'sourceType' is null")
                    .or().apply("metadata ->> 'sourceType' = {0}", MetadataKeys.SOURCE_TYPE_USER));
            return;
        }
        wrapper.apply("metadata ->> 'sourceType' = {0}", sourceType);
    }

    /**
     * 只保留用户端允许写入的 metadata，避免通过普通文档接口改写来源边界。
     */
    private Map<String, Object> userMetadataUpdate(Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (metadata != null) {
            result.putAll(metadata);
        }
        result.remove(MetadataKeys.SOURCE_TYPE);
        result.remove(MetadataKeys.SOURCE_ID);
        result.remove(MetadataKeys.OWNER_USER_ID);
        return result;
    }

    /**
     * 将用户 ID 注入元数据映射，确保元数据中包含归属信息。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param metadata 原始元数据映射
     * @return 包含用户 ID 的元数据映射
     */
    private Map<String, Object> ownerMetadata(UUID ownerUserId, Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (metadata != null) {
            result.putAll(metadata);
        }
        result.put(MetadataKeys.OWNER_USER_ID, ownerUserId.toString());
        return result;
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