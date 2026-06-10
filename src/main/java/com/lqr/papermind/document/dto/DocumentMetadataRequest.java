package com.lqr.papermind.document.dto;

import com.lqr.papermind.document.service.DocumentPersistenceService;

import java.util.Map;

/**
 * 文档元数据更新请求。
 */
public record DocumentMetadataRequest(
        String title,
        Object authors,
        String abstractText,
        String doi,
        String journal,
        Integer publishYear,
        Object keywords,
        Map<String, Object> metadata
) {
    public DocumentPersistenceService.DocumentMetadataUpdate toUpdate() {
        return new DocumentPersistenceService.DocumentMetadataUpdate(
                title,
                authors,
                abstractText,
                doi,
                journal,
                publishYear,
                keywords,
                metadata
        );
    }
}