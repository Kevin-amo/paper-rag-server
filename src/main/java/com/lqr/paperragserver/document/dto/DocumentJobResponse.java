package com.lqr.paperragserver.document.dto;

import com.lqr.paperragserver.document.entity.DocumentIngestionJob;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentJobResponse(
        UUID jobId,
        UUID ownerUserId,
        String sourceId,
        String fileName,
        String title,
        String status,
        int progress,
        String errorMessage,
        int retryCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
    public static DocumentJobResponse from(DocumentIngestionJob job) {
        return new DocumentJobResponse(
                job.getId(),
                job.getOwnerUserId(),
                job.getSourceId(),
                job.getFileName(),
                job.getTitle(),
                job.getStatus(),
                job.getProgress() == null ? 0 : job.getProgress(),
                job.getErrorMessage(),
                job.getRetryCount() == null ? 0 : job.getRetryCount(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}