package com.lqr.papermind.literature.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 最近一次外部文献搜索的结构化上下文，用于支持会话内追问和筛选。
 *
 * @param query 搜索关键词
 * @param limit 返回结果数量上限
 * @param sortBy 排序方式
 * @param dateFrom 起始日期
 * @param dateTo 截止日期
 * @param categories 分类筛选条件列表
 * @param items 上一轮搜索结果列表
 * @param sourceConversationId 来源会话标识
 * @param sourceMessageId 来源消息标识
 * @param createdAt 搜索发生时间
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
    /**
     * 创建搜索上下文，并将集合字段固定为不可变空集合或副本。
     */
    public LiteratureSearchContext {
        categories = categories == null ? List.of() : List.copyOf(categories);
        items = items == null ? List.of() : List.copyOf(items);
    }
}