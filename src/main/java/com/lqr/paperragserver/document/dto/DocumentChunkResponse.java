package com.lqr.paperragserver.document.dto;

import com.lqr.paperragserver.document.service.DocumentPersistenceService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DocumentChunkResponse(
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
    public static DocumentChunkResponse from(DocumentPersistenceService.DocumentChunkView chunk) {
        return new DocumentChunkResponse(
                chunk.chunkId(),
                chunk.ownerUserId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.contentHash(),
                chunk.chunkStart(),
                chunk.chunkEnd(),
                chunk.pageNumber(),
                chunk.sectionTitle(),
                chunk.metadata(),
                chunk.vectorStoreId(),
                chunk.createdAt(),
                chunk.updatedAt()
        );
    }
}