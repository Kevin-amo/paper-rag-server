package com.lqr.paperragserver.literature.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * 文献搜索请求。
 */
public record LiteratureSearchRequest(
        UUID conversationId,
        @NotBlank(message = "搜索关键词不能为空") String query,
        @Positive(message = "limit 必须为正数") Integer limit,
        List<String> categories,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "dateFrom 格式必须为 YYYY-MM-DD") String dateFrom,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "dateTo 格式必须为 YYYY-MM-DD") String dateTo,
        String sortBy
) {
    /**
     * 用兼容旧调用方的参数创建搜索请求，默认不绑定会话，且不设置结束日期。
     */
    public LiteratureSearchRequest(String query, Integer limit, List<String> categories, String dateFrom, String sortBy) {
        this(null, query, limit, categories, dateFrom, null, sortBy);
    }

    /**
     * 用兼容旧调用方的参数创建搜索请求，默认不绑定会话。
     */
    public LiteratureSearchRequest(String query, Integer limit, List<String> categories, String dateFrom, String dateTo, String sortBy) {
        this(null, query, limit, categories, dateFrom, dateTo, sortBy);
    }
}