package com.lqr.paperragserver.review.risk;

import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ReferenceFormatChecker {
    private static final Pattern REFERENCE_SPLIT_PATTERN = Pattern.compile("(?m)(?=^\\s*(?:\\[\\d+]|\\d+[.)\\u3001])\\s*)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");

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
        return REFERENCE_SPLIT_PATTERN.splitAsStream(referencesText)
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private boolean isRecentYear(int year) {
        return year >= Year.now().getValue() - 4;
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