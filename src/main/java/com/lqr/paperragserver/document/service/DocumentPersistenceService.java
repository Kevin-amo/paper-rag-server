package com.lqr.paperragserver.document.service;

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
public interface DocumentPersistenceService {

    /**
     * 分页查询文档摘要。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param keyword 关键词过滤条件
     * @param status 状态过滤条件
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 分页查询结果
     */
    PageResult<DocumentSummary> listDocuments(UUID ownerUserId, String keyword, String status, int page, int size);

    /**
     * 按来源 ID 查询文档详情。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @return 文档详情，不存在时返回空
     */
    Optional<DocumentDetail> findDocument(UUID ownerUserId, String sourceId);

    /**
     * 按来源 ID 查询指定用户的任意来源文档详情。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @return 文档详情，不存在时返回空
     */
    Optional<DocumentDetail> findAnyDocument(UUID ownerUserId, String sourceId);

    /**
     * 按来源 ID 查询指定用户的评审文档详情。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @return 文档详情，不存在时返回空
     */
    Optional<DocumentDetail> findReviewDocument(UUID ownerUserId, String sourceId);

    /**
     * 根据 sourceId 列表批量查询已索引文档状态。
     * 返回 sourceId -> isIndexed 的映射，仅包含 deletedAt 为空且 status 为 INDEXED 的文档。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceIds 文档来源标识列表
     * @return sourceId 到是否已索引的映射
     */
    Map<String, Boolean> findIndexedDocuments(UUID ownerUserId, List<String> sourceIds);

    /**
     * 分页查询指定文档的分块。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 分页查询结果
     */
    PageResult<DocumentChunkView> listChunks(UUID ownerUserId, String sourceId, int page, int size);

    /**
     * 从已索引文档分块中执行关键词检索。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param question 检索问题
     * @param limit 最大返回条数
     * @return 匹配的文档分块列表
     */
    List<DocumentChunk> searchChunks(UUID ownerUserId, String question, int limit);

    /**
     * 更新文档的可编辑元数据。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param update 元数据更新内容
     */
    void updateMetadata(UUID ownerUserId, String sourceId, DocumentMetadataUpdate update);

    /**
     * 恢复已删除文档。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     */
    void restore(UUID ownerUserId, String sourceId);

    /**
     * 标记文档进入指定处理状态。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param status 目标状态
     * @param progress 进度百分比
     */
    void markStatus(UUID ownerUserId, String sourceId, String status, int progress);

    /**
     * 标记文档进入解析中状态并保存正文。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param source 文档来源信息
     * @param contentText 文档正文文本
     */
    void markParsing(UUID ownerUserId, DocumentSource source, String contentText);

    /**
     * 替换指定文档的二进制资产列表。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param assets 新的资产列表
     */
    void replaceAssets(UUID ownerUserId, String sourceId, List<DocumentAsset> assets);

    /**
     * 查询指定文档的资产列表，可按资产 ID 过滤。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param assetIds 需要过滤的资产 ID 列表，为空时不做过滤
     * @return 资产视图列表
     */
    List<DocumentAssetView> listAssets(UUID ownerUserId, String sourceId, List<String> assetIds);

    /**
     * 查询指定文档下的单个资产。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param assetId 资产 ID
     * @return 资产视图，不存在时返回空
     */
    Optional<DocumentAssetView> findAsset(UUID ownerUserId, String sourceId, String assetId);

    /**
     * 替换指定文档的分块列表。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param chunks 新的分块列表
     */
    void replaceChunks(UUID ownerUserId, String sourceId, List<DocumentChunk> chunks);

    /**
     * 标记文档索引完成。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param chunkCount 分块数量
     */
    void markIndexed(UUID ownerUserId, String sourceId, int chunkCount);

    /**
     * 标记文档处理失败。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     * @param errorMessage 错误信息
     */
    void markFailed(UUID ownerUserId, String sourceId, String errorMessage);

    /**
     * 软删除指定文档。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param sourceId 文档来源标识
     */
    void markDeleted(UUID ownerUserId, String sourceId);

    /**
     * 软删除当前用户的全部有效文档。
     *
     * @param ownerUserId 文档所属用户 ID
     */
    void markAllDeleted(UUID ownerUserId);

    record PageResult<T>(List<T> items, int page, int size, long total) {
    }

    record DocumentSummary(
            String sourceId,
            UUID ownerUserId,
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
            UUID ownerUserId,
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
            UUID ownerUserId,
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
            UUID ownerUserId,
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