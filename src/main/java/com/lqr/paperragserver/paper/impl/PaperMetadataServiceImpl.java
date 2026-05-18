package com.lqr.paperragserver.paper.impl;

import com.lqr.paperragserver.common.constant.MetadataKeys;
import com.lqr.paperragserver.common.model.DocumentSource;
import com.lqr.paperragserver.paper.service.PaperMetadataService;
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
public class PaperMetadataServiceImpl implements PaperMetadataService {

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
     */
    @Override
    public DocumentSource enrich(DocumentSource source, Map<String, Object> paperMetadata) {
        if (source == null) {
            throw new IllegalArgumentException("文档来源信息不能为空");
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (source.metadata() != null) {
            merged.putAll(source.metadata());
        }
        if (paperMetadata != null) {
            merged.putAll(paperMetadata);
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

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

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

    private Integer validYear(int year) {
        return year >= MIN_PUBLISH_YEAR && year <= MAX_PUBLISH_YEAR ? year : null;
    }

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