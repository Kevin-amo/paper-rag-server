package com.lqr.papermind.review.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReviewOutputParser {

    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入 Jackson ObjectMapper 用于 JSON 解析。
     *
     * @param objectMapper Jackson 对象映射器
     */
    public ReviewOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析模型输出的评审文本，提取并返回结构化的评审结果。
     *
     * @param modelText 模型返回的原始文本
     * @return 解析后的评审结果映射（包含 paperSections、scores、comments、risks 等字段）
     * @throws IllegalArgumentException 模型结果为空或不是有效 JSON 时抛出
     */
    public Map<String, Object> parse(String modelText) {
        String json = extractJson(modelText);
        Map<String, Object> parsed = readJson(json);
        normalize(parsed);
        return parsed;
    }

    /**
     * 将 JSON 字符串解析为 Map。若首次解析失败，尝试修复常见格式问题后重试。
     *
     * @param json JSON 字符串
     * @return 解析后的映射
     * @throws IllegalArgumentException 修复后仍无法解析时抛出
     */
    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            String repairedJson = repairJson(json);
            try {
                return objectMapper.readValue(repairedJson, new TypeReference<Map<String, Object>>() {
                });
            } catch (JsonProcessingException ignored) {
                throw new IllegalArgumentException("模型评审结果不是有效 JSON", ignored);
            }
        }
    }

    /**
     * 从模型输出文本中提取第一个完整的 JSON 对象。
     * 支持去除代码围栏（```json ... ```）。
     *
     * @param value 模型原始输出文本
     * @return 提取到的 JSON 字符串
     * @throws IllegalArgumentException 输入为空、缺少 JSON 对象或 JSON 不完整时抛出
     */
    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("模型评审结果为空");
        }
        String text = stripCodeFence(value.trim());
        int start = text.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("模型评审结果缺少 JSON 对象");
        }
        int end = balancedObjectEnd(text, start);
        if (end < 0) {
            throw new IllegalArgumentException("模型评审结果 JSON 对象不完整");
        }
        return text.substring(start, end + 1);
    }

    /**
     * 去除文本中的 Markdown 代码围栏标记。
     *
     * @param value 可能包含围栏的文本
     * @return 去除围栏后的纯文本
     */
    private String stripCodeFence(String value) {
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        return text;
    }

    /**
     * 查找从 start 位置开始的匹配花括号的结束位置。
     * 正确处理字符串字面量和转义字符。
     *
     * @param text  源文本
     * @param start 起始左花括号的位置
     * @return 匹配的右花括号位置，未找到时返回 -1
     */
    private int balancedObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * 修复 JSON 中常见的格式问题：移除尾部逗号。
     *
     * @param json 原始 JSON 字符串
     * @return 修复后的 JSON 字符串
     */
    private String repairJson(String json) {
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }

    /**
     * 对解析后的评审结果进行标准化处理。
     * 填充缺失字段的默认值，并对分数进行钳位。
     *
     * @param parsed 待标准化的评审结果映射（会被原地修改）
     */
    private void normalize(Map<String, Object> parsed) {
        parsed.putIfAbsent("paperSections", new LinkedHashMap<>());
        parsed.putIfAbsent("scores", List.of());
        parsed.putIfAbsent("comments", new LinkedHashMap<>());
        parsed.putIfAbsent("risks", List.of());
        parsed.put("totalScore", clamp(intValue(parsed.get("totalScore"), 0), 0, 100));
        normalizeScores(parsed.get("scores"));
    }

    /**
     * 标准化评分明细列表中的每个评分项。
     * 对 maxScore、score、confidence 进行类型转换和钳位。
     *
     * @param scores 评分明细列表（原始类型）
     */
    private void normalizeScores(Object scores) {
        if (!(scores instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> scoreMap = (Map<String, Object>) rawMap;
                int maxScore = clamp(intValue(scoreMap.get("maxScore"), 100), 1, 100);
                int score = clamp(intValue(scoreMap.get("score"), 0), 0, maxScore);
                double confidence = clamp(doubleValue(scoreMap.get("confidence"), 0.0), 0.0, 1.0);
                scoreMap.put("maxScore", maxScore);
                scoreMap.put("score", score);
                scoreMap.put("confidence", confidence);
            }
        }
    }

    /**
     * 将任意值安全转换为 int 类型。支持 Number 和可解析的字符串。
     *
     * @param value    原始值
     * @param fallback 转换失败时的默认值
     * @return 转换后的整数值
     */
    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 将任意值安全转换为 double 类型。支持 Number 和可解析的字符串。
     *
     * @param value    原始值
     * @param fallback 转换失败时的默认值
     * @return 转换后的双精度浮点值
     */
    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 将整数值限制在 [min, max] 范围内。
     *
     * @param value 原始值
     * @param min   最小值
     * @param max   最大值
     * @return 钳位后的整数值
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 将浮点值限制在 [min, max] 范围内。
     *
     * @param value 原始值
     * @param min   最小值
     * @param max   最大值
     * @return 钳位后的浮点值
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
