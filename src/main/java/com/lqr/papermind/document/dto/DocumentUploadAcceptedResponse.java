package com.lqr.papermind.document.dto;

import com.lqr.papermind.document.entity.DocumentIngestionJob;

import java.util.UUID;

/**
 * 文档上传受理响应，返回异步入库任务的基础状态。
 */
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