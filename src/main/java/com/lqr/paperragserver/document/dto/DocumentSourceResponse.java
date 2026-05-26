package com.lqr.paperragserver.document.dto;

import com.lqr.paperragserver.common.model.DocumentSource;

import java.util.Map;

public record DocumentSourceResponse(
        String sourceId,
        String title,
        String origin,
        Map<String, Object> metadata
) {
    public static DocumentSourceResponse from(DocumentSource source) {
        return new DocumentSourceResponse(source.sourceId(), source.title(), source.origin(), source.metadata());
    }
}