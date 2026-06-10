package com.lqr.papermind.document.dto;

import com.lqr.papermind.common.model.DocumentSource;

import java.util.Map;

/**
 * 文档来源信息的接口响应视图。
 */
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