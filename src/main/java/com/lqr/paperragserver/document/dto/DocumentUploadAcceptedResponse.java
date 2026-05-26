package com.lqr.paperragserver.document.dto;

import com.lqr.paperragserver.document.entity.DocumentIngestionJob;

import java.util.UUID;

public record DocumentUploadAcceptedResponse(
        UUID jobId,
        String sourceId,
        String status,
        String message
) {
    public static DocumentUploadAcceptedResponse from(DocumentIngestionJob job) {
        return new DocumentUploadAcceptedResponse(job.getId(), job.getSourceId(), job.getStatus(), "文档已进入异步入库队列");
    }
}