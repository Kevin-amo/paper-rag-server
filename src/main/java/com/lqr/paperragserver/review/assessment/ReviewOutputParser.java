package com.lqr.paperragserver.review.assessment;

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

    public ReviewOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parse(String modelText) {
        String json = extractJson(modelText);
        Map<String, Object> parsed = readJson(json);
        normalize(parsed);
        return parsed;
    }

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
                throw new IllegalArgumentException("\u6a21\u578b\u8bc4\u5ba1\u7ed3\u679c\u4e0d\u662f\u6709\u6548 JSON", ignored);
            }
        }
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("\u6a21\u578b\u8bc4\u5ba1\u7ed3\u679c\u4e3a\u7a7a");
        }
        String text = stripCodeFence(value.trim());
        int start = text.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("\u6a21\u578b\u8bc4\u5ba1\u7ed3\u679c\u7f3a\u5c11 JSON \u5bf9\u8c61");
        }
        int end = balancedObjectEnd(text, start);
        if (end < 0) {
            throw new IllegalArgumentException("\u6a21\u578b\u8bc4\u5ba1\u7ed3\u679c JSON \u5bf9\u8c61\u4e0d\u5b8c\u6574");
        }
        return text.substring(start, end + 1);
    }

    private String stripCodeFence(String value) {
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        return text;
    }

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

    private String repairJson(String json) {
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }

    private void normalize(Map<String, Object> parsed) {
        parsed.putIfAbsent("paperSections", new LinkedHashMap<>());
        parsed.putIfAbsent("scores", List.of());
        parsed.putIfAbsent("comments", new LinkedHashMap<>());
        parsed.putIfAbsent("risks", List.of());
        parsed.put("totalScore", clamp(intValue(parsed.get("totalScore"), 0), 0, 100));
        normalizeScores(parsed.get("scores"));
    }

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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
