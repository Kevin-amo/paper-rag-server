package com.lqr.paperragserver.document.dto;

import com.lqr.paperragserver.document.service.DocumentPersistenceService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DocumentAssetResponse(
        String assetId,
        String sourceId,
        UUID ownerUserId,
        int assetIndex,
        String assetType,
        String fileName,
        String contentType,
        Long fileSize,
        String contentHash,
        String extractedText,
        Integer textStart,
        Integer textEnd,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DocumentAssetResponse from(DocumentPersistenceService.DocumentAssetView asset) {
        return new DocumentAssetResponse(
                asset.assetId(),
                asset.sourceId(),
                asset.ownerUserId(),
                asset.assetIndex(),
                asset.assetType(),
                asset.fileName(),
                asset.contentType(),
                asset.fileSize(),
                asset.contentHash(),
                asset.extractedText(),
                asset.textStart(),
                asset.textEnd(),
                asset.metadata(),
                asset.createdAt(),
                asset.updatedAt()
        );
    }
}