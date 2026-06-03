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

    /**
     * 对文本做安全截取，将连续空白压缩为单个空格后截断至指定长度，超出部分以省略号代替。
     *
     * @param text      原始文本，为 null 时返回空字符串
     * @param maxLength 最大保留长度，小于等于 3 时不追加省略号
     * @return 安全截取后的文本摘要
     */
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

    /**
     * 使用默认最大长度（160 字符）对文本做安全截取。
     *
     * @param text 原始文本，为 null 时返回空字符串
     * @return 安全截取后的文本摘要
     */
    public static String safeExcerpt(String text) {
        return safeExcerpt(text, DEFAULT_EXCERPT_LENGTH);
    }

    /**
     * 将文本中的连续空白字符压缩为单个空格，并去除首尾空白。
     *
     * @param text 原始文本，为 null 时返回空字符串
     * @return 归一化后的文本
     */
    public static String normalizeWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    /**
     * 生成 Map 的安全摘要，仅保留键数量和键名集合，避免泄露值内容。
     *
     * @param map 原始键值映射，为 null 或空时返回固定空摘要
     * @return 包含 keyCount 和 keys 的安全摘要
     */
    public static Map<String, Object> safeMapSummary(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Map.of("keyCount", 0, "keys", Set.of());
        }
        return Map.of(
                "keyCount", map.size(),
                "keys", new TreeSet<>(map.keySet())
        );
    }

    /**
     * 生成工具调用输入的安全摘要，提取查询文本长度和摘要，以及白名单中的安全参数。
     *
     * @param input 工具调用输入参数映射，为 null 或空时返回空映射
     * @return 包含 queryLength、queryExcerpt、安全参数和键名集合的摘要
     */
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

    /**
     * 生成 URI 的安全摘要，提取协议、主机、路径和查询参数键名，不记录参数值。
     *
     * @param uri 原始 URI，为 null 时返回空映射
     * @return 包含 scheme、host、path、hasQuery 和 queryKeys 的摘要
     */
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

    /**
     * 从原始查询字符串中提取所有参数键名。
     *
     * @param rawQuery 原始查询字符串，为 null 或空白时返回空集合
     * @return 排序后的参数键名集合
     */
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