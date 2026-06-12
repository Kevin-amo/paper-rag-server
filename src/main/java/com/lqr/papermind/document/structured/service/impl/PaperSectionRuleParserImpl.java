package com.lqr.papermind.document.structured.service.impl;

import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.model.PaperStructuredContent;
import com.lqr.papermind.document.structured.model.PaperStructuredContentSupport;
import com.lqr.papermind.document.structured.model.StructuredFieldEvidence;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.PaperSectionRuleParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向中文、英文论文常见章节标题的规则解析器。
 */
@Component
public class PaperSectionRuleParserImpl implements PaperSectionRuleParser {

    private static final int MAX_SECTION_LENGTH = 8000;
    private static final int MAX_HEADING_LENGTH = 120;
    private static final Pattern NUMBERED_PREFIX = Pattern.compile("^(?:第?[一二三四五六七八九十百]+[章节、.]|[0-9]+(?:\\.[0-9]+)*[.)、]?|[IVXLCDM]+[.)、])\\s*");
    private static final Pattern NUMBERED_HEADING_PREFIX = Pattern.compile("^(?:第?[一二三四五六七八九十百]+[章节、.]|[0-9]+(?:\\.[0-9]+)*[.)、]?|[IVXLCDM]+[.)、])\\s*(.+)$");
    private static final Pattern KEYWORDS_LINE = Pattern.compile("^(?:关键词|关键字|keywords?)\\s*[:：]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final List<String> GENERIC_HEADING_ALIASES = List.of(
            "摘要", "abstract", "关键词", "关键字", "keywords",
            "引言", "绪论", "导论", "前言", "背景", "研究背景", "项目背景", "课题背景", "introduction", "background",
            "文献综述", "相关研究", "相关技术", "理论基础", "研究现状", "国内外研究现状", "related work", "related works", "literature review",
            "研究方法", "方法", "方法设计", "材料与方法", "需求分析", "系统设计", "总体设计", "详细设计", "method", "methods", "methodology", "approach",
            "实验", "实验结果", "结果", "结果分析", "实验与分析", "测试", "系统测试", "功能测试", "测试结果", "运行结果", "结果展示", "results", "experiments", "experimental results", "evaluation",
            "讨论", "分析与讨论", "discussion",
            "结论", "总结", "结语", "总结与展望", "conclusion", "conclusions",
            "参考文献", "references", "bibliography", "致谢", "acknowledgment", "acknowledgments"
    );
    private static final Map<String, List<String>> SECTION_ALIASES = Map.of(
            "abstract", List.of("摘要", "abstract"),
            "introduction", List.of("引言", "绪论", "导论", "前言", "背景", "研究背景", "项目背景", "课题背景", "introduction", "background"),
            "literatureReview", List.of("文献综述", "相关研究", "相关技术", "理论基础", "研究现状", "国内外研究现状", "related work", "related works", "literature review"),
            "methodology", List.of("研究方法", "方法", "方法设计", "材料与方法", "需求分析", "系统设计", "总体设计", "详细设计", "功能设计", "实现方案", "method", "methods", "methodology", "approach"),
            "experimentResults", List.of("实验", "实验结果", "结果", "结果分析", "实验与分析", "测试", "系统测试", "功能测试", "性能测试", "测试结果", "运行结果", "结果展示", "系统运行", "results", "experiments", "experimental results", "evaluation"),
            "discussion", List.of("讨论", "分析与讨论", "问题分析", "结果讨论", "discussion"),
            "conclusion", List.of("结论", "总结", "结语", "总结与展望", "conclusion", "conclusions"),
            "references", List.of("参考文献", "references", "bibliography")
    );

    @Override
    public StructuredParseResult parse(DocumentPersistenceService.DocumentDetail document) {
        String text = document.contentText() == null ? "" : document.contentText();
        Map<String, String> sections = detectSections(text);
        List<String> keywords = keywords(document, text);
        PaperStructuredContent content = new PaperStructuredContent(
                safeTitle(document.title()),
                bestAbstract(document.abstractText(), sections.get("abstract"), text),
                sections.get("introduction"),
                sections.get("literatureReview"),
                sections.get("methodology"),
                sections.get("experimentResults"),
                sections.get("discussion"),
                sections.get("conclusion"),
                sections.get("references"),
                keywords,
                null,
                null,
                List.of(),
                null,
                null,
                List.of()
        );
        Map<String, StructuredFieldEvidence> evidence = buildEvidence(content, sections);
        List<String> missingFields = PaperStructuredContentSupport.emptyFields(content);
        List<String> lowConfidenceFields = evidence.values().stream()
                .filter(item -> !item.missing() && item.confidence() < 0.7)
                .map(StructuredFieldEvidence::fieldName)
                .toList();
        return new StructuredParseResult(content, evidence, missingFields, lowConfidenceFields);
    }

    private Map<String, String> detectSections(String text) {
        Map<String, SectionBuilder> builders = new LinkedHashMap<>();
        String currentField = null;
        String[] lines = text.split("\\R");
        for (String rawLine : lines) {
            String line = normalizeLine(rawLine);
            if (line.isBlank()) {
                continue;
            }
            String field = headingField(line);
            if (field != null) {
                currentField = field;
                builders.putIfAbsent(field, new SectionBuilder(line));
                String inlineContent = inlineHeadingContent(line);
                if (!inlineContent.isBlank()) {
                    builders.get(field).append(inlineContent);
                }
                continue;
            }
            if (looksLikeAnyHeading(line)) {
                currentField = null;
                continue;
            }
            if (currentField != null) {
                builders.get(currentField).append(line);
            }
        }
        Map<String, String> sections = new LinkedHashMap<>();
        builders.forEach((field, builder) -> {
            String value = truncate(builder.text(), MAX_SECTION_LENGTH);
            if (!value.isBlank()) {
                sections.put(field, value);
            }
        });
        return sections;
    }

    private String headingField(String line) {
        String candidate = stripHeading(line);
        if (candidate.length() > MAX_HEADING_LENGTH) {
            return null;
        }
        String normalized = candidate.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : SECTION_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                String normalizedAlias = alias.toLowerCase(Locale.ROOT);
                if (sameHeading(normalized, normalizedAlias)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean looksLikeAnyHeading(String line) {
        String candidate = stripHeading(line);
        if (candidate.length() > MAX_HEADING_LENGTH) {
            return false;
        }
        String normalized = candidate.toLowerCase(Locale.ROOT);
        for (String alias : GENERIC_HEADING_ALIASES) {
            if (sameHeading(normalized, alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return NUMBERED_HEADING_PREFIX.matcher(line.trim()).matches() && candidate.length() <= 40;
    }

    private boolean sameHeading(String normalized, String normalizedAlias) {
        return normalized.equals(normalizedAlias)
                || normalized.startsWith(normalizedAlias + "：")
                || normalized.startsWith(normalizedAlias + ":")
                || normalized.startsWith(normalizedAlias + "-")
                || normalized.startsWith(normalizedAlias + "—");
    }

    private String stripHeading(String line) {
        String candidate = NUMBERED_PREFIX.matcher(line.trim()).replaceFirst("").trim();
        candidate = candidate.replaceAll("^[#\\s]+", "").replaceAll("[：:]+$", "").trim();
        return candidate.replaceAll("(?<=\\p{IsHan})\\d{1,3}$", "").trim();
    }

    private String inlineHeadingContent(String line) {
        int colon = Math.max(line.indexOf('：'), line.indexOf(':'));
        if (colon < 0 || colon + 1 >= line.length()) {
            return "";
        }
        return line.substring(colon + 1).trim();
    }

    private List<String> keywords(DocumentPersistenceService.DocumentDetail document, String text) {
        List<String> metadataKeywords = PaperStructuredContentSupport.texts(document.keywords());
        if (!metadataKeywords.isEmpty()) {
            return metadataKeywords;
        }
        for (String rawLine : text.split("\\R")) {
            Matcher matcher = KEYWORDS_LINE.matcher(normalizeLine(rawLine));
            if (matcher.find()) {
                return PaperStructuredContentSupport.texts(matcher.group(1));
            }
        }
        return List.of();
    }

    private Map<String, StructuredFieldEvidence> buildEvidence(PaperStructuredContent content, Map<String, String> sections) {
        Map<String, StructuredFieldEvidence> evidence = new LinkedHashMap<>();
        for (String field : PaperStructuredContentSupport.ALL_FIELDS) {
            Object value = PaperStructuredContentSupport.value(content, field);
            boolean missing = PaperStructuredContentSupport.isEmpty(value);
            double confidence = missing ? 0.0 : confidence(field, sections.containsKey(field));
            String sourceEvidence = missing ? null : evidenceText(field, sections.containsKey(field));
            evidence.put(field, new StructuredFieldEvidence(field, "RULE", confidence, missing, sourceEvidence));
        }
        return evidence;
    }

    private double confidence(String field, boolean sectionHit) {
        if ("title".equals(field) || "keywords".equals(field)) {
            return 0.85;
        }
        if ("abstract".equals(field)) {
            return sectionHit ? 0.9 : 0.75;
        }
        return sectionHit ? 0.86 : 0.0;
    }

    private String evidenceText(String field, boolean sectionHit) {
        if (sectionHit) {
            return "命中章节标题";
        }
        if ("title".equals(field) || "abstract".equals(field) || "keywords".equals(field)) {
            return "来自文档元数据或正文特征";
        }
        return null;
    }

    private String safeTitle(String value) {
        return blankToNull(value);
    }

    private String bestAbstract(String metadataAbstract, String sectionAbstract, String fullText) {
        String sectionValue = blankToNull(sectionAbstract);
        if (sectionValue != null) {
            return sectionValue;
        }
        String metadataValue = blankToNull(metadataAbstract);
        if (isUsefulAbstract(metadataValue, fullText)) {
            return metadataValue;
        }
        return null;
    }

    private boolean isUsefulAbstract(String value, String fullText) {
        if (value == null) {
            return false;
        }
        String normalized = normalizeLine(value).toLowerCase(Locale.ROOT);
        if (normalized.length() < 20) {
            return false;
        }
        if (normalized.contains("generated by") || normalized.contains("python-docx") || normalized.contains("microsoft word")) {
            return false;
        }
        String normalizedFullText = normalizeLine(fullText);
        return normalizedFullText.isBlank() || normalizedFullText.contains(value.trim());
    }

    private String normalizeLine(String value) {
        return value == null ? "" : value.replace('　', ' ').trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static class SectionBuilder {
        private final List<String> lines = new ArrayList<>();

        SectionBuilder(String heading) {
        }

        void append(String line) {
            if (line != null && !line.isBlank()) {
                lines.add(line);
            }
        }

        String text() {
            return String.join("\n", lines).trim();
        }
    }
}
