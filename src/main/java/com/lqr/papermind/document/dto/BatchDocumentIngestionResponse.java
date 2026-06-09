package com.lqr.papermind.document.dto;

import java.util.List;

/**
 * 批量文档入库接口的整体响应。
 */
public record BatchDocumentIngestionResponse(
        List<BatchDocumentIngestionItemResponse> items,
        int acceptedCount,
        int failureCount
) {
}