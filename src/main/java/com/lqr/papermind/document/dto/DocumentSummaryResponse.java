package com.lqr.papermind.document.dto;

import com.lqr.papermind.document.service.DocumentPersistenceService;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 文档列表摘要的接口响应视图。
 */
public record DocumentSummaryResponse(
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
    public static DocumentSummaryResponse from(DocumentPersistenceService.DocumentSummary document) {
        return new DocumentSummaryResponse(
                document.sourceId(),
                document.ownerUserId(),
                document.title(),
                document.origin(),
                document.fileName(),
                document.fileType(),
                document.fileSize(),
                document.status(),
                document.chunkCount(),
                document.publishYear(),
                document.createdAt(),
                document.updatedAt()
        );
    }
}