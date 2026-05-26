package com.lqr.paperragserver.document.dto;

import com.lqr.paperragserver.document.service.DocumentPersistenceService;

import java.util.Map;

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