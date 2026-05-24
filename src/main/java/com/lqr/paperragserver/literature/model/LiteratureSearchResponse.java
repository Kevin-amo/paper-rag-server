package com.lqr.paperragserver.literature.model;

import java.util.List;
import java.util.UUID;

/**
 * 统一文献搜索响应。
 */
public record LiteratureSearchResponse(
        UUID conversationId,
        String summary,
        List<LiteratureSearchResult> items
) {
    public LiteratureSearchResponse {
        if (items == null) {
            items = List.of();
        }
    }

    public LiteratureSearchResponse(List<LiteratureSearchResult> items) {
        this(null, null, items);
    }
}