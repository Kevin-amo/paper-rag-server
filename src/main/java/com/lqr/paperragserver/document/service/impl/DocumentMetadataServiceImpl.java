package com.lqr.paperragserver.document.service.impl;

import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.document.service.DocumentMetadataService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 默认论文元数据归一化服务。
 */
@Service
public class DocumentMetadataServiceImpl implements DocumentMetadataService {

    private static final List<String> AUTHOR_KEYS = List.of("authors", "author", "作者");
    private static final List<String> ABSTRACT_KEYS = List.of("abstractText", "abstract", "summary", "摘要");
    private static final List<String> DOI_KEYS = List.of("doi", "DOI");
    private static final List<String> JOURNAL_KEYS = List.of("journal", "venue", "publication", "期刊");
    private static final List<String> PUBLISH_YEAR_KEYS = List.of("publishYear", "publicationYear", "year", "年份");
    private static final List<String> KEYWORD_KEYS = List.of("keywords", "keyword", "tags", "关键词");
    private static final int MIN_PUBLISH_YEAR = 1500;
    private static final int MAX_PUBLISH_YEAR = 3000;

    /**
     * 将外部传入的论文元数据归一化后合并到文档来源 metadata。
     *
     * @param source 原始文档来源信息
     * @param documentMetadata 论文领域元数据
     * @return 补全后的文档来源信息
     */
    @Override
    public DocumentSource enrich(DocumentSource source, Map<String, Object> documentMetadata) {
        if (source == null) {
            throw new IllegalArgumentException("文档来源信息不能为空");
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (source.metadata() != null) {
            merged.putAll(source.metadata());
        }
        if (documentMetadata != null) {
            merged.putAll(documentMetadata);
        }

        putIfPresent(merged, MetadataKeys.AUTHORS, normalizeList(firstValue(merged, AUTHOR_KEYS)));
        putIfPresent(merged, MetadataKeys.ABSTRACT_TEXT, normalizeText(firstValue(merged, ABSTRACT_KEYS)));
        putIfPresent(merged, MetadataKeys.DOI, normalizeDoi(firstValue(merged, DOI_KEYS)));
        putIfPresent(merged, MetadataKeys.JOURNAL, normalizeText(firstValue(merged, JOURNAL_KEYS)));
        putIfPresent(merged, MetadataKeys.PUBLISH_YEAR, normalizePublishYear(firstValue(merged, PUBLISH_YEAR_KEYS)));
        putIfPresent(merged, MetadataKeys.KEYWORDS, normalizeList(firstValue(merged, KEYWORD_KEYS)));

        String title = normalizeText(merged.get(MetadataKeys.TITLE));
        return new DocumentSource(
                source.sourceId(),
                title == null ? source.title() : title,
                source.origin(),
                merged
        );
    }

    /**
     * 从元数据映射中按候选键列表查找第一个非空值。
     *
     * @param metadata 元数据映射
     * @param keys 候选键列表
     * @return 第一个匹配的非空值，均未命中时返回 null
     */
    private Object firstValue(Map<String, Object> metadata, List<String> keys) {
        for (String key : keys) {
            if (metadata.containsKey(key)) {
                Object value = metadata.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * 当值非空时写入元数据映射。
     *
     * @param metadata 元数据映射
     * @param key 键名
     * @param value 待写入的值
     */
    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    /**
     * 将值归一化为非空白字符串，空白时返回 null。
     *
     * @param value 原始值
     * @return 归一化后的文本，空白时返回 null
     */
    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    /**
     * 归一化 DOI 标识，移除 URL 前缀和 "doi:" 前缀。
     *
     * @param value 原始 DOI 值
     * @return 归一化后的 DOI，空白时返回 null
     */
    private String normalizeDoi(Object value) {
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        String normalized = text.replaceFirst("(?i)^https?://(?:dx\\.)?doi\\.org/", "")
                .replaceFirst("(?i)^doi:\\s*", "")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * 归一化发表年份，从数值或字符串中提取合法的四位年份。
     *
     * @param value 原始年份值
     * @return 合法的发表年份，不合法时返回 null
     */
    private Integer normalizePublishYear(Object value) {
        if (value instanceof Number number) {
            return validYear(number.intValue());
        }
        String text = normalizeText(value);
        if (text == null) {
            return null;
        }
        try {
            return validYear(Integer.parseInt(text.replaceFirst("^.*?(\\d{4}).*$", "$1")));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 校验年份是否在合理范围内。
     *
     * @param year 待校验年份
     * @return 合法时返回年份，否则返回 null
     */
    private Integer validYear(int year) {
        return year >= MIN_PUBLISH_YEAR && year <= MAX_PUBLISH_YEAR ? year : null;
    }

    /**
     * 将值归一化为字符串列表，支持数组、集合和逗号/分号分隔的字符串。
     *
     * @param value 原始值
     * @return 归一化后的字符串列表，空列表时返回 null
     */
    private List<String> normalizeList(Object value) {
        if (value == null) {
            return null;
        }
        List<String> items = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addListItem(items, item);
            }
        } else if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                addListItem(items, java.lang.reflect.Array.get(value, index));
            }
        } else {
            for (String item : String.valueOf(value).split("[,;，；]")) {
                addListItem(items, item);
            }
        }
        return items.isEmpty() ? null : items;
    }

    /**
     * 将单个条目归一化后追加到列表，自动去重（忽略大小写）。
     *
     * @param items 目标列表
     * @param item 待追加的条目
     */
    private void addListItem(List<String> items, Object item) {
        String text = normalizeText(item);
        if (text == null) {
            return;
        }
        String key = text.toLowerCase(Locale.ROOT);
        boolean exists = items.stream().anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(key));
        if (!exists) {
            items.add(text);
        }
    }
}