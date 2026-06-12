package com.lqr.papermind.literature.model;

import java.util.List;
import java.util.UUID;

/**
 * 统一文献搜索响应。
 *
 * @param conversationId 关联的会话标识，可为空
 * @param summary 搜索结果摘要文本，可为空
 * @param items 搜索结果列表
 */
public record LiteratureSearchResponse(
        UUID conversationId,
        String summary,
        List<LiteratureSearchResult> items
) {
    /**
     * 创建统一搜索响应，并将空结果列表规范化为空集合。
     */
    public LiteratureSearchResponse {
        if (items == null) {
            items = List.of();
        }
    }

    /**
     * 用搜索结果列表创建无会话、无摘要的响应。
     */
    public LiteratureSearchResponse(List<LiteratureSearchResult> items) {
        this(null, null, items);
    }
}