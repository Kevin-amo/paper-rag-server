package com.lqr.papermind.review.risk;

import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ReferenceFormatChecker {
    private static final Pattern NUMBERED_REFERENCE_PATTERN = Pattern.compile("^\\s*(?:\\[\\d+]|\\d+[.)、])\\s+.*");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern AUTHOR_PREFIX_PATTERN = Pattern.compile("^[A-Z][A-Za-z'-]*(?:\\.|\\s+[A-Z]\\.)\\s+.+");
    private static final Pattern CJK_REFERENCE_PREFIX_PATTERN = Pattern.compile("^[\\p{IsHan}]{2,20}[.．。]\\s*.+");

    /**
     * 对参考文献文本进行格式合规性检查。
     * 检查每条引用是否包含发表年份，以及是否包含近5年内的文献。
     *
     * @param referencesText 参考文献原始文本
     * @return 检测到的风险项列表，无风险时返回空列表
     */
    public List<ReferenceRisk> check(String referencesText) {
        if (referencesText == null || referencesText.isBlank()) {
            return Collections.emptyList();
        }

        List<String> entries = splitReferences(referencesText);
        List<ReferenceRisk> risks = new ArrayList<>();
        List<Integer> years = new ArrayList<>();

        for (String entry : entries) {
            var matcher = YEAR_PATTERN.matcher(entry);
            if (matcher.find()) {
                years.add(Integer.parseInt(matcher.group(1)));
            } else {
                risks.add(new ReferenceRisk(
                        "REFERENCE_FORMAT",
                        "MEDIUM",
                        entry,
                        "Add the missing publication year to the reference.",
                        0.85
                ));
            }
        }

        if (years.size() >= 2 && years.stream().noneMatch(this::isRecentYear)) {
            risks.add(new ReferenceRisk(
                    "REFERENCE_OUTDATED",
                    "LOW",
                    "No dated reference is within the recent 5 years.",
                    "Add recent references published within the last 5 years.",
                    0.75
            ));
        }

        return risks;
    }

    /**
     * 将参考文献文本按条目进行拆分。
     * 支持编号格式（如 [1]、1.、1)、1、）和非编号的独立引用行。
     *
     * @param referencesText 参考文献原始文本
     * @return 拆分后的引用条目列表
     */
    private List<String> splitReferences(String referencesText) {
        List<String> entries = new ArrayList<>();
        StringBuilder currentEntry = new StringBuilder();
        boolean currentEntryStartedNumbered = false;

        for (String rawLine : referencesText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || isHeadingLine(line)) {
                continue;
            }

            if (isNumberedReferenceLine(line)) {
                appendEntry(entries, currentEntry);
                currentEntry.append(line);
                currentEntryStartedNumbered = true;
            } else if (currentEntry.isEmpty()) {
                if (looksLikeStandaloneReferenceLine(line)) {
                    currentEntry.append(line);
                    currentEntryStartedNumbered = false;
                }
            } else if (currentEntryStartedNumbered && !hasYear(currentEntry) && hasYear(line)) {
                currentEntry.append(System.lineSeparator()).append(line);
            } else if (looksLikeStandaloneReferenceLine(line)) {
                appendEntry(entries, currentEntry);
                currentEntry.append(line);
                currentEntryStartedNumbered = false;
            } else {
                currentEntry.append(System.lineSeparator()).append(line);
            }
        }

        appendEntry(entries, currentEntry);
        return entries;
    }

    /**
     * 将当前正在构建的引用条目追加到结果列表中，并重置 StringBuilder。
     *
     * @param entries      结果列表
     * @param currentEntry 当前正在构建的条目缓冲区
     */
    private void appendEntry(List<String> entries, StringBuilder currentEntry) {
        if (!currentEntry.isEmpty()) {
            entries.add(currentEntry.toString().trim());
            currentEntry.setLength(0);
        }
    }

    /**
     * 判断当前行是否为编号格式的引用行（如 [1] xxx 或 1. xxx）。
     *
     * @param line 待检测的行文本
     * @return 是编号引用行时返回 true
     */
    private boolean isNumberedReferenceLine(String line) {
        return NUMBERED_REFERENCE_PATTERN.matcher(line).matches();
    }

    /**
     * 判断当前行是否为参考文献章节标题行。
     *
     * @param line 待检测的行文本
     * @return 是标题行时返回 true
     */
    private boolean isHeadingLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT).replace(":", "").trim();
        return normalized.equals("references")
                || normalized.equals("reference")
                || normalized.equals("bibliography")
                || normalized.equals("参考文献");
    }

    /**
     * 判断当前行是否像是一条独立的引用（包含年份、作者前缀或中文文献前缀）。
     *
     * @param line 待检测的行文本
     * @return 看起来像独立引用时返回 true
     */
    private boolean looksLikeStandaloneReferenceLine(String line) {
        return hasYear(line)
                || AUTHOR_PREFIX_PATTERN.matcher(line).matches()
                || CJK_REFERENCE_PREFIX_PATTERN.matcher(line).matches();
    }

    /**
     * 判断文本中是否包含年份（1900-2099 范围）。
     *
     * @param text 待检测的文本
     * @return 包含年份时返回 true
     */
    private boolean hasYear(CharSequence text) {
        return YEAR_PATTERN.matcher(text).find();
    }

    /**
     * 判断给定年份是否为近5年内的年份。
     *
     * @param year 待判断的年份
     * @return 近5年内时返回 true
     */
    private boolean isRecentYear(int year) {
        return Year.now().getValue() - year <= 5;
    }

    /**
     * 参考文献风险项记录。
     *
     * @param riskType   风险类型
     * @param riskLevel  风险等级
     * @param evidence   问题证据
     * @param suggestion 修复建议
     * @param confidence 置信度
     */
    public record ReferenceRisk(
            String riskType,
            String riskLevel,
            String evidence,
            String suggestion,
            double confidence
    ) {
    }
}
