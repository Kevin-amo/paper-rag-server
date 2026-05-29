package com.lqr.paperragserver.common.logging;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 日志安全摘要工具，避免将密钥、大文本、完整正文和完整 prompt 写入日志。
 */
public final class LogSanitizer {

    private static final int DEFAULT_EXCERPT_LENGTH = 160;
    private static final Set<String> SAFE_ACTION_KEYS = Set.of(
            "topK",
            "limit",
            "sortBy",
            "dateFrom",
            "categories"
    );

    private LogSanitizer() {
    }

    public static String safeExcerpt(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        int safeMaxLength = Math.max(0, maxLength);
        String normalized = normalizeWhitespace(text);
        if (normalized.length() <= safeMaxLength) {
            return normalized;
        }
        if (safeMaxLength <= 3) {
            return normalized.substring(0, safeMaxLength);
        }
        return normalized.substring(0, safeMaxLength - 3) + "...";
    }

    public static String safeExcerpt(String text) {
        return safeExcerpt(text, DEFAULT_EXCERPT_LENGTH);
    }

    public static String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    public static Map<String, Object> safeMapSummary(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Map.of("keyCount", 0, "keys", Set.of());
        }
        return Map.of(
                "keyCount", map.size(),
                "keys", new TreeSet<>(map.keySet())
        );
    }

    public static Map<String, Object> safeActionInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        Object query = input.get("query");
        if (query != null) {
            String queryText = String.valueOf(query);
            summary.put("queryLength", normalizeWhitespace(queryText).length());
            summary.put("queryExcerpt", safeExcerpt(queryText, 120));
        }
        for (String key : SAFE_ACTION_KEYS) {
            if (input.containsKey(key)) {
                Object value = input.get(key);
                summary.put(key, value == null ? null : String.valueOf(value));
            }
        }
        summary.put("keys", new TreeSet<>(input.keySet()));
        return summary;
    }

    public static Map<String, Object> safeUriSummary(URI uri) {
        if (uri == null) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scheme", uri.getScheme());
        summary.put("host", uri.getHost());
        summary.put("path", uri.getPath());
        summary.put("hasQuery", uri.getRawQuery() != null && !uri.getRawQuery().isBlank());
        summary.put("queryKeys", queryKeys(uri.getRawQuery()));
        return summary;
    }

    private static Set<String> queryKeys(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Set.of();
        }
        Set<String> keys = new TreeSet<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            keys.add(equalsIndex < 0 ? pair : pair.substring(0, equalsIndex));
        }
        return keys;
    }
}