package com.lqr.paperragserver.literature.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 最近一次外部文献搜索的结构化上下文，用于支持会话内追问和筛选。
 */
public record LiteratureSearchContext(
        String query,
        Integer limit,
        String sortBy,
        String dateFrom,
        String dateTo,
        List<String> categories,
        List<LiteratureSearchResult> items,
        UUID sourceConversationId,
        UUID sourceMessageId,
        OffsetDateTime createdAt
) {
    public LiteratureSearchContext {
        categories = categories == null ? List.of() : List.copyOf(categories);
        items = items == null ? List.of() : List.copyOf(items);
    }
}