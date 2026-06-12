package com.lqr.papermind.document.structured.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.ai.service.PromptConstructionService;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.model.ModelCompletionResult;
import com.lqr.papermind.document.structured.model.PaperStructuredContent;
import com.lqr.papermind.document.structured.model.PaperStructuredContentSupport;
import com.lqr.papermind.document.structured.model.StructuredFieldEvidence;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.PaperStructuredModelCompleter;
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
public class PaperStructuredModelCompleterImpl implements PaperStructuredModelCompleter {

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
            PromptConstructionService.Prompt prompt = buildPrompt(document, ruleResult, targetFields);
            rawOutput = llmService.generate(prompt);
            ParsedModelOutput parsedOutput = parseJsonWithRepair(rawOutput, prompt, targetFields);
            rawOutput = parsedOutput.rawOutput();
            Map<String, Object> parsed = parsedOutput.parsed();
            PaperStructuredContent content = PaperStructuredContentSupport.fromMap(parsed);
            Map<String, StructuredFieldEvidence> evidence = evidence(content, targetFields);
            return new ModelCompletionResult(
                    new StructuredParseResult(content, evidence, PaperStructuredContentSupport.emptyFields(content), lowConfidenceFields(evidence)),
                    rawOutput,
                    null
            );
        } catch (ModelOutputParseException ex) {
            return fallbackResult(ruleResult, ex.rawOutput(), safeMessage(ex));
        } catch (RuntimeException ex) {
            return fallbackResult(ruleResult, rawOutput, safeMessage(ex));
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
        String systemMessage = "你是论文结构化解析助手。只输出一个严格 JSON 对象；第一个字符必须是 {，最后一个字符必须是 }。禁止 Markdown、代码围栏、解释文字、前后缀。";
        String userMessage = "请基于论文文本补全结构化解析字段。\n"
                + "只补全这些字段：" + targetFields + "\n"
                + "输出 JSON 必须包含且只包含以下键：title, abstract, introduction, literatureReview, methodology, experimentResults, discussion, conclusion, references, keywords, researchObject, researchQuestion, innovationPoints, methodPath, experimentDataSummary, mainConclusions。\n"
                + "keywords、innovationPoints、mainConclusions 必须是字符串数组；其他字段为字符串或 null。不能编造论文没有依据的内容，没有依据时返回 null 或空数组。\n\n"
                + "输出模板：\n" + jsonTemplate() + "\n\n"
                + "文档元数据：\n"
                + "标题：" + nullToEmpty(document.title()) + "\n"
                + "摘要：" + nullToEmpty(document.abstractText()) + "\n"
                + "关键词：" + jsonText(document.keywords()) + "\n\n"
                + "规则解析结果：\n" + jsonText(ruleResult.content()) + "\n\n"
                + "论文全文片段：\n" + truncate(document.contentText(), FULL_TEXT_LIMIT);
        return new PromptConstructionService.Prompt(systemMessage, userMessage);
    }

    private ParsedModelOutput parseJsonWithRepair(String firstOutput,
                                                  PromptConstructionService.Prompt originalPrompt,
                                                  List<String> targetFields) {
        try {
            return new ParsedModelOutput(parseJson(firstOutput), firstOutput);
        } catch (RuntimeException firstFailure) {
            String repairOutput = llmService.generate(repairPrompt(originalPrompt, firstOutput, firstFailure.getMessage(), targetFields));
            String combinedOutput = combinedRawOutput(firstOutput, repairOutput);
            try {
                return new ParsedModelOutput(parseJson(repairOutput), combinedOutput);
            } catch (RuntimeException repairFailure) {
                throw new ModelOutputParseException(combinedOutput, repairFailure);
            }
        }
    }

    private String combinedRawOutput(String firstOutput, String repairOutput) {
        return "第一次输出：\n" + nullToEmpty(firstOutput) + "\n\n修复输出：\n" + nullToEmpty(repairOutput);
    }

    private PromptConstructionService.Prompt repairPrompt(PromptConstructionService.Prompt originalPrompt,
                                                          String badOutput,
                                                          String parseError,
                                                          List<String> targetFields) {
        String systemMessage = "你是 JSON 修复器。只输出一个严格 JSON 对象；第一个字符必须是 {，最后一个字符必须是 }。禁止 Markdown、解释文字、前后缀。";
        String userMessage = "上一次论文结构化解析输出无法被系统解析。\n"
                + "解析错误：" + nullToEmpty(parseError) + "\n"
                + "上一次输出：\n" + nullToEmpty(badOutput) + "\n\n"
                + "请重新基于原始任务输出 JSON。只补全这些字段：" + targetFields + "\n"
                + "JSON 必须包含且只包含以下键：title, abstract, introduction, literatureReview, methodology, experimentResults, discussion, conclusion, references, keywords, researchObject, researchQuestion, innovationPoints, methodPath, experimentDataSummary, mainConclusions。\n"
                + "keywords、innovationPoints、mainConclusions 必须是字符串数组；其他字段为字符串或 null。没有依据时返回 null 或空数组。\n\n"
                + "输出模板：\n" + jsonTemplate() + "\n\n"
                + "原始任务：\n" + originalPrompt.userMessage();
        return new PromptConstructionService.Prompt(systemMessage, userMessage);
    }

    private String jsonTemplate() {
        return """
                {"title":null,"abstract":null,"introduction":null,"literatureReview":null,"methodology":null,"experimentResults":null,"discussion":null,"conclusion":null,"references":null,"keywords":[],"researchObject":null,"researchQuestion":null,"innovationPoints":[],"methodPath":null,"experimentDataSummary":null,"mainConclusions":[]}
                """.trim();
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

    private ModelCompletionResult fallbackResult(StructuredParseResult ruleResult, String rawOutput, String errorMessage) {
        PaperStructuredContent content = ruleResult.content();
        content = putIfMissing(content, "researchObject", inferResearchObject(content));
        content = putIfMissing(content, "researchQuestion", firstSentence(content.introduction(), content.methodology()));
        content = putIfMissing(content, "methodPath", firstSentence(content.methodology()));
        content = putIfMissing(content, "experimentDataSummary", firstSentence(content.experimentResults()));
        content = putIfMissing(content, "mainConclusions", conclusionItems(content.conclusion()));

        Map<String, StructuredFieldEvidence> evidence = PaperStructuredContentSupport.emptyEvidence("MODEL");
        for (String field : List.of("researchObject", "researchQuestion", "methodPath", "experimentDataSummary", "mainConclusions")) {
            Object value = PaperStructuredContentSupport.value(content, field);
            if (!PaperStructuredContentSupport.isEmpty(value)) {
                evidence.put(field, new StructuredFieldEvidence(field, "RULE_DERIVED", 0.62, false, "模型补全失败后由规则解析内容兜底生成"));
            }
        }
        return new ModelCompletionResult(
                new StructuredParseResult(content, evidence, PaperStructuredContentSupport.emptyFields(content), lowConfidenceFields(evidence)),
                rawOutput,
                errorMessage
        );
    }

    private PaperStructuredContent putIfMissing(PaperStructuredContent content, String field, Object value) {
        if (PaperStructuredContentSupport.isEmpty(PaperStructuredContentSupport.value(content, field)) && !PaperStructuredContentSupport.isEmpty(value)) {
            return PaperStructuredContentSupport.withValue(content, field, value);
        }
        return content;
    }

    private String inferResearchObject(PaperStructuredContent content) {
        String title = content.title();
        if (title != null) {
            String cleaned = title.replace("课程设计", "").replace("论文", "").trim();
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return firstSentence(content.abstractText(), content.introduction());
    }

    private String firstSentence(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.strip();
            String[] parts = normalized.split("(?<=[。！？!?])\\s*|\\R+");
            for (String part : parts) {
                String sentence = part.strip();
                if (!sentence.isBlank()) {
                    return sentence.length() <= 240 ? sentence : sentence.substring(0, 240);
                }
            }
        }
        return null;
    }

    private List<String> conclusionItems(String conclusion) {
        String sentence = firstSentence(conclusion);
        return sentence == null ? List.of() : List.of(sentence);
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

    private static class ModelOutputParseException extends IllegalStateException {
        private final String rawOutput;

        private ModelOutputParseException(String rawOutput, Throwable cause) {
            super(cause.getMessage(), cause);
            this.rawOutput = rawOutput;
        }

        private String rawOutput() {
            return rawOutput;
        }
    }

    private record ParsedModelOutput(Map<String, Object> parsed, String rawOutput) {
    }
}
