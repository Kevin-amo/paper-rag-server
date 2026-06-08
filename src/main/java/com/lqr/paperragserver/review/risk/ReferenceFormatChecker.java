package com.lqr.paperragserver.review.risk;

import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ReferenceFormatChecker {
    private static final Pattern NUMBERED_REFERENCE_PATTERN = Pattern.compile("^\\s*(?:\\[\\d+]|\\d+[.)\\u3001])\\s+.*");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern AUTHOR_PREFIX_PATTERN = Pattern.compile("^[A-Z][A-Za-z'-]*(?:\\.|\\s+[A-Z]\\.)\\s+.+");
    private static final Pattern CJK_REFERENCE_PREFIX_PATTERN = Pattern.compile("^[\\p{IsHan}]{2,20}[.\\uFF0E\\u3002]\\s*.+");

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

    private void appendEntry(List<String> entries, StringBuilder currentEntry) {
        if (!currentEntry.isEmpty()) {
            entries.add(currentEntry.toString().trim());
            currentEntry.setLength(0);
        }
    }

    private boolean isNumberedReferenceLine(String line) {
        return NUMBERED_REFERENCE_PATTERN.matcher(line).matches();
    }

    private boolean isHeadingLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT).replace(":", "").trim();
        return normalized.equals("references")
                || normalized.equals("reference")
                || normalized.equals("bibliography")
                || normalized.equals("\u53C2\u8003\u6587\u732E");
    }

    private boolean looksLikeStandaloneReferenceLine(String line) {
        return hasYear(line)
                || AUTHOR_PREFIX_PATTERN.matcher(line).matches()
                || CJK_REFERENCE_PREFIX_PATTERN.matcher(line).matches();
    }

    private boolean hasYear(CharSequence text) {
        return YEAR_PATTERN.matcher(text).find();
    }

    private boolean isRecentYear(int year) {
        return Year.now().getValue() - year <= 5;
    }

    public record ReferenceRisk(
            String riskType,
            String riskLevel,
            String evidence,
            String suggestion,
            double confidence
    ) {
    }
}