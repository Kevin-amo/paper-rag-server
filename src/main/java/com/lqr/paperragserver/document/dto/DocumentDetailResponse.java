package com.lqr.paperragserver.document.dto;

import com.lqr.paperragserver.document.service.DocumentPersistenceService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DocumentDetailResponse(
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
    public static DocumentDetailResponse from(DocumentPersistenceService.DocumentDetail document) {
        return new DocumentDetailResponse(
                document.sourceId(),
                document.ownerUserId(),
                document.title(),
                document.origin(),
                document.fileName(),
                document.fileType(),
                document.fileSize(),
                document.authors(),
                document.abstractText(),
                document.doi(),
                document.journal(),
                document.publishYear(),
                document.keywords(),
                document.contentText(),
                document.metadata(),
                document.status(),
                document.chunkCount(),
                document.errorMessage(),
                document.createdAt(),
                document.updatedAt(),
                document.deletedAt()
        );
    }
}