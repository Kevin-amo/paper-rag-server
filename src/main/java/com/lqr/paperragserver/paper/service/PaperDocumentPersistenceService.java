package com.lqr.paperragserver.paper.service;

import com.lqr.paperragserver.common.DocumentChunk;
import com.lqr.paperragserver.common.DocumentSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 文档元数据持久化服务。
 */
public interface PaperDocumentPersistenceService {

    PageResult<DocumentSummary> listDocuments(String keyword, String status, int page, int size);

    Optional<DocumentDetail> findDocument(String sourceId);

    PageResult<DocumentChunkView> listChunks(String sourceId, int page, int size);

    List<DocumentChunk> searchChunks(String question, int limit);

    void updateMetadata(String sourceId, DocumentMetadataUpdate update);

    void restore(String sourceId);

    void markParsing(DocumentSource source, String contentText);

    void replaceChunks(String sourceId, List<DocumentChunk> chunks);

    void markIndexed(String sourceId, int chunkCount);

    void markFailed(String sourceId, String errorMessage);

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