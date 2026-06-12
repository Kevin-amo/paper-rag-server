package com.lqr.papermind.document.structured.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 结构化论文内容的字段级辅助能力。
 */
public final class PaperStructuredContentSupport {

    public static final List<String> ALL_FIELDS = List.of(
            "title",
            "abstract",
            "introduction",
            "literatureReview",
            "methodology",
            "experimentResults",
            "discussion",
            "conclusion",
            "references",
            "keywords",
            "researchObject",
            "researchQuestion",
            "innovationPoints",
            "methodPath",
            "experimentDataSummary",
            "mainConclusions"
    );

    private PaperStructuredContentSupport() {
    }

    public static PaperStructuredContent emptyContent() {
        return new PaperStructuredContent(null, null, null, null, null, null, null, null, null, List.of(), null, null, List.of(), null, null, List.of());
    }

    public static Map<String, StructuredFieldEvidence> emptyEvidence(String source) {
        Map<String, StructuredFieldEvidence> evidence = new LinkedHashMap<>();
        for (String field : ALL_FIELDS) {
            evidence.put(field, new StructuredFieldEvidence(field, source, 0.0, true, null));
        }
        return evidence;
    }

    public static List<String> emptyFields(PaperStructuredContent content) {
        List<String> fields = new ArrayList<>();
        for (String field : ALL_FIELDS) {
            if (isEmpty(value(content, field))) {
                fields.add(field);
            }
        }
        return fields;
    }

    public static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    public static Object value(PaperStructuredContent content, String field) {
        if (content == null) {
            return null;
        }
        return switch (field) {
            case "title" -> content.title();
            case "abstract" -> content.abstractText();
            case "introduction" -> content.introduction();
            case "literatureReview" -> content.literatureReview();
            case "methodology" -> content.methodology();
            case "experimentResults" -> content.experimentResults();
            case "discussion" -> content.discussion();
            case "conclusion" -> content.conclusion();
            case "references" -> content.references();
            case "keywords" -> content.keywords();
            case "researchObject" -> content.researchObject();
            case "researchQuestion" -> content.researchQuestion();
            case "innovationPoints" -> content.innovationPoints();
            case "methodPath" -> content.methodPath();
            case "experimentDataSummary" -> content.experimentDataSummary();
            case "mainConclusions" -> content.mainConclusions();
            default -> null;
        };
    }

    public static PaperStructuredContent fromMap(Map<String, Object> map) {
        if (map == null) {
            return emptyContent();
        }
        return new PaperStructuredContent(
                text(map.get("title")),
                text(map.get("abstract")),
                text(map.get("introduction")),
                text(map.get("literatureReview")),
                text(map.get("methodology")),
                text(map.get("experimentResults")),
                text(map.get("discussion")),
                text(map.get("conclusion")),
                text(map.get("references")),
                texts(map.get("keywords")),
                text(map.get("researchObject")),
                text(map.get("researchQuestion")),
                texts(map.get("innovationPoints")),
                text(map.get("methodPath")),
                text(map.get("experimentDataSummary")),
                texts(map.get("mainConclusions"))
        );
    }

    public static PaperStructuredContent withValue(PaperStructuredContent content, String field, Object value) {
        Map<String, Object> values = toMap(content);
        values.put(field, value);
        return fromMap(values);
    }

    public static Map<String, Object> toMap(PaperStructuredContent content) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : ALL_FIELDS) {
            values.put(field, value(content, field));
        }
        return values;
    }

    public static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).findFirst().orElse(null);
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static List<String> texts(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return List.of();
        }
        String[] parts = text.split("[，,；;、\\n]+");
        List<String> items = new ArrayList<>();
        for (String part : parts) {
            String item = part.trim();
            if (!item.isEmpty() && !items.contains(item)) {
                items.add(item);
            }
        }
        return items;
    }
}