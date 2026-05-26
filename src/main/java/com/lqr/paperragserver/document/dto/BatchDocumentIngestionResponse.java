package com.lqr.paperragserver.document.dto;

import java.util.List;

public record BatchDocumentIngestionResponse(
        List<BatchDocumentIngestionItemResponse> items,
        int acceptedCount,
        int failureCount
) {
}