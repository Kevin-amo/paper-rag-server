package com.lqr.papermind.literature.model;

import java.util.List;

/**
 * 统一论文搜索结果。
 *
 * @param title 论文标题
 * @param authors 作者列表
 * @param abstractText 摘要文本
 * @param year 发表年份
 * @param publishedDate 发表日期
 * @param updatedDate 更新日期
 * @param categories 分类列表
 * @param primaryCategory 主分类
 * @param doi 数字对象标识符
 * @param url 论文网页地址
 * @param pdfUrl PDF 下载地址
 * @param source 数据来源标识，例如 openalex
 * @param externalId 外部系统中的唯一标识
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