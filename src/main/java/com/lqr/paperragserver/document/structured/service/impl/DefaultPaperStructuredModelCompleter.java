package com.lqr.paperragserver.document.structured.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.document.service.DocumentPersistenceService;
import com.lqr.paperragserver.document.structured.model.ModelCompletionResult;
import com.lqr.paperragserver.document.structured.model.PaperStructuredContent;
import com.lqr.paperragserver.document.structured.model.PaperStructuredContentSupport;
import com.lqr.paperragserver.document.structured.model.StructuredFieldEvidence;
import com.lqr.paperragserver.document.structured.model.StructuredParseResult;
import com.lqr.paperragserver.document.structured.service.PaperStructuredModelCompleter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Qwen/DashScope 的结构化字段补全器。
 */
@Component
@RequiredArgsConstructor
public class DefaultPaperStructuredModelCompleter implements PaperStructuredModelCompleter {

    private static final int FULL_TEXT_LIMIT = 12000;

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public ModelCompletionResult complete(DocumentPersistenceService.DocumentDetail document, StructuredParseResult ruleResult) {
        List<String> targetFields = targetFields(ruleResult);
        if (targetFields.isEmpty()) {
            return ModelCompletionResult.empty();
        }
        String rawOutput = null;
        try {
            rawOutput = llmService.generate(buildPrompt(document, ruleResult, targetFields));
            Map<String, Object> parsed = parseJson(rawOutput);
            PaperStructuredContent content = PaperStructuredContentSupport.fromMap(parsed);
            Map<String, StructuredFieldEvidence> evidence = evidence(content, targetFields);
            return new ModelCompletionResult(
                    new StructuredParseResult(content, evidence, PaperStructuredContentSupport.emptyFields(content), lowConfidenceFields(evidence)),
                    rawOutput,
                    null
            );
        } catch (RuntimeException ex) {
            return new ModelCompletionResult(ModelCompletionResult.empty().result(), rawOutput, safeMessage(ex));
        }
    }

    private List<String> targetFields(StructuredParseResult ruleResult) {
        return PaperStructuredContentSupport.ALL_FIELDS.stream()
                .filter(field -> ruleResult.missingFields().contains(field) || ruleResult.lowConfidenceFields().contains(field))
                .toList();
    }

    private PromptConstructionService.Prompt buildPrompt(DocumentPersistenceService.DocumentDetail document,
                                                         StructuredParseResult ruleResult,
                                                         List<String> targetFields) {
        String systemMessage = "你是论文结构化解析助手。只输出严格 JSON，不要 Markdown，不要解释性前后缀。";
        String userMessage = "请基于论文文本补全结构化解析字段。\n"
                + "只补全这些字段：" + targetFields + "\n"
                + "输出 JSON 必须只包含以下键：title, abstract, introduction, literatureReview, methodology, experimentResults, discussion, conclusion, references, keywords, researchObject, researchQuestion, innovationPoints, methodPath, experimentDataSummary, mainConclusions。\n"
                + "keywords、innovationPoints、mainConclusions 必须是字符串数组；其他字段为字符串或 null。不能编造论文没有依据的内容，没有依据时返回 null 或空数组。\n\n"
                + "文档元数据：\n"
                + "标题：" + nullToEmpty(document.title()) + "\n"
                + "摘要：" + nullToEmpty(document.abstractText()) + "\n"
                + "关键词：" + jsonText(document.keywords()) + "\n\n"
                + "规则解析结果：\n" + jsonText(ruleResult.content()) + "\n\n"
                + "论文全文片段：\n" + truncate(document.contentText(), FULL_TEXT_LIMIT);
        return new PromptConstructionService.Prompt(systemMessage, userMessage);
    }

    private Map<String, Object> parseJson(String value) {
        String json = extractJson(value);
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Object structured = parsed.get("structuredContent");
            if (structured instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, item) -> result.put(String.valueOf(key), item));
                return result;
            }
            return parsed;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("模型结构化解析结果不是有效 JSON", ex);
        }
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("模型结构化解析结果为空");
        }
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("模型结构化解析结果缺少 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private Map<String, StructuredFieldEvidence> evidence(PaperStructuredContent content, List<String> targetFields) {
        Map<String, StructuredFieldEvidence> evidence = new LinkedHashMap<>();
        for (String field : PaperStructuredContentSupport.ALL_FIELDS) {
            Object value = PaperStructuredContentSupport.value(content, field);
            boolean missing = PaperStructuredContentSupport.isEmpty(value);
            double confidence = !missing && targetFields.contains(field) ? 0.72 : 0.0;
            evidence.put(field, new StructuredFieldEvidence(field, "MODEL", confidence, missing, missing ? null : "模型补全"));
        }
        return evidence;
    }

    private List<String> lowConfidenceFields(Map<String, StructuredFieldEvidence> evidence) {
        return evidence.values().stream()
                .filter(item -> !item.missing() && item.confidence() < 0.7)
                .map(StructuredFieldEvidence::fieldName)
                .toList();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "\n[后续内容因长度限制已截断]";
    }

    private String jsonText(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "模型补全失败" : message;
    }
}