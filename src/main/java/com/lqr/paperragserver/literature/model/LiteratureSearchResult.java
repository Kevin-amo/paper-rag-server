package com.lqr.paperragserver.literature.model;

import java.util.List;

/**
 * 统一论文搜索结果。
 */
public record LiteratureSearchResult(
        String title,
        List<String> authors,
        String abstractText,
        Integer year,
        String publishedDate,
        String updatedDate,
        List<String> categories,
        String primaryCategory,
        String doi,
        String url,
        String pdfUrl,
        String source,
        String externalId
) {
    /**
     * 创建统一搜索结果，并补齐作者、分类和来源的默认表达。
     */
    public LiteratureSearchResult {
        if (authors == null) {
            authors = List.of();
        }
        if (categories == null) {
            categories = List.of();
        }
        if (source == null || source.isBlank()) {
            source = "openalex";
        }
    }
}