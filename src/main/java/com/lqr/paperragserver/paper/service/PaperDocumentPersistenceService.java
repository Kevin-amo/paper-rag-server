package com.lqr.paperragserver.paper.service;

import com.lqr.paperragserver.common.model.DocumentAsset;
import com.lqr.paperragserver.common.model.DocumentChunk;
import com.lqr.paperragserver.common.model.DocumentSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 文档元数据持久化服务。
 */
public interface PaperDocumentPersistenceService {

    /**
     * 分页查询文档摘要。
     */
    PageResult<DocumentSummary> listDocuments(String keyword, String status, int page, int size);

    /**
     * 按来源 ID 查询文档详情。
     */
    Optional<DocumentDetail> findDocument(String sourceId);

    /**
     * 分页查询指定文档的分块。
     */
    PageResult<DocumentChunkView> listChunks(String sourceId, int page, int size);

    /**
     * 从已索引文档分块中执行关键词检索。
     */
    List<DocumentChunk> searchChunks(String question, int limit);

    /**
     * 更新文档的可编辑元数据。
     */
    void updateMetadata(String sourceId, DocumentMetadataUpdate update);

    /**
     * 恢复已删除文档。
     */
    void restore(String sourceId);

    /**
     * 标记文档进入解析中状态并保存正文。
     */
    void markParsing(DocumentSource source, String contentText);

    /**
     * 替换指定文档的二进制资产列表。
     */
    void replaceAssets(String sourceId, List<DocumentAsset> assets);

    /**
     * 查询指定文档的资产列表，可按资产 ID 过滤。
     */
    List<DocumentAssetView> listAssets(String sourceId, List<String> assetIds);

    /**
     * 查询指定文档下的单个资产。
     */
    Optional<DocumentAssetView> findAsset(String sourceId, String assetId);

    /**
     * 替换指定文档的分块列表。
     */
    void replaceChunks(String sourceId, List<DocumentChunk> chunks);

    /**
     * 标记文档索引完成。
     */
    void markIndexed(String sourceId, int chunkCount);

    /**
     * 标记文档处理失败。
     */
    void markFailed(String sourceId, String errorMessage);

    /**
     * 软删除指定文档。
     */
    void markDeleted(String sourceId);

    record PageResult<T>(List<T> items, int page, int size, long total) {
    }

    record DocumentSummary(
            String sourceId,
            String title,
            String origin,
            String fileName,
            String fileType,
            Long fileSize,
            String status,
            int chunkCount,
            Integer publishYear,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    record DocumentDetail(
            String sourceId,
            String title,
            String origin,
            String fileName,
            String fileType,
            Long fileSize,
            Object authors,
            String abstractText,
            String doi,
            String journal,
            Integer publishYear,
            Object keywords,
            String contentText,
            Map<String, Object> metadata,
            String status,
            int chunkCount,
            String errorMessage,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt
    ) {
    }

    record DocumentChunkView(
            String chunkId,
            int chunkIndex,
            String content,
            String contentHash,
            Integer chunkStart,
            Integer chunkEnd,
            Integer pageNumber,
            String sectionTitle,
            Map<String, Object> metadata,
            UUID vectorStoreId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    record DocumentAssetView(
            String assetId,
            String sourceId,
            int assetIndex,
            String assetType,
            String fileName,
            String contentType,
            Long fileSize,
            String contentHash,
            byte[] content,
            String extractedText,
            Integer textStart,
            Integer textEnd,
            Map<String, Object> metadata,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    record DocumentMetadataUpdate(
            String title,
            Object authors,
            String abstractText,
            String doi,
            String journal,
            Integer publishYear,
            Object keywords,
            Map<String, Object> metadata
    ) {
    }
}