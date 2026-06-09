package com.lqr.papermind.document.dto;

import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.service.DocumentIngestionJobService;

import java.util.UUID;

/**
 * 批量文档入库的单个文件处理结果。
 */
public record BatchDocumentIngestionItemResponse(
        int index,
        String fileName,
        boolean accepted,
        String errorMessage,
        UUID jobId,
        String sourceId,
        String status,
        String message
) {
    public static BatchDocumentIngestionItemResponse accepted(int index, String fileName, DocumentIngestionJob job) {
        return new BatchDocumentIngestionItemResponse(
                index,
                fileName,
                true,
                null,
                job.getId(),
                job.getSourceId(),
                job.getStatus(),
                "文档已进入异步入库队列"
        );
    }

    public static BatchDocumentIngestionItemResponse failure(int index, String fileName, String errorMessage) {
        return new BatchDocumentIngestionItemResponse(index, fileName, false, errorMessage, null, null, DocumentIngestionJobService.STATUS_FAILED, "任务创建失败");
    }
}