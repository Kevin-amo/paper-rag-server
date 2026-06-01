package com.lqr.paperragserver.agent.paper;

import com.lqr.paperragserver.agent.core.AgentDecision;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LiteratureContextPolicy {

    private final LiteratureSearchIntentParser intentParser;

    public void applySearchHints(Map<String, Object> input, String question, LiteratureSearchContext context) {
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question, context);
        if (hasText(intent.query())) {
            input.put("query", intent.query());
        }
        if (intent.limit() != null) {
            input.put("limit", intent.limit());
        }
        if (hasText(intent.sortBy())) {
            input.put("sortBy", intent.sortBy());
        }
        if (hasText(intent.dateFrom())) {
            input.put("dateFrom", intent.dateFrom());
        }
        if (hasText(intent.dateTo())) {
            input.put("dateTo", intent.dateTo());
        }
        if (intent.categories() != null && !intent.categories().isEmpty()) {
            input.put("categories", intent.categories());
        } else {
            input.putIfAbsent("categories", List.of());
        }
    }

    public boolean isFollowUp(String question, LiteratureSearchContext context) {
        if (context == null || question == null) {
            return false;
        }
        return intentParser.parse(question, context).followUp();
    }

    public AgentDecision finishFromPreviousItems(String question,
                                                 LiteratureSearchContext context,
                                                 List<String> observations) {
        if (context == null || observations != null && !observations.isEmpty()) {
            return null;
        }
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(question, context);
        if (!intent.withinPreviousItems() || intent.dateFrom() == null || intent.dateTo() == null) {
            return null;
        }
        List<LiteratureSearchResult> matches = filterPreviousItems(context.items(), intent.dateFrom(), intent.dateTo());
        if (matches.isEmpty()) {
            return null;
        }
        String answer = "上一轮文献结果中有 " + matches.size() + " 篇符合条件：\n" + formatMatchedLiterature(matches);
        return AgentDecision.finish("上一轮文献结果中已有可回答的筛选结果。", answer);
    }

    private List<LiteratureSearchResult> filterPreviousItems(List<LiteratureSearchResult> items, String dateFrom, String dateTo) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LocalDate from = LocalDate.parse(dateFrom);
        LocalDate to = LocalDate.parse(dateTo);
        return items.stream()
                .filter(item -> withinDateRange(item, from, to))
                .toList();
    }

    private boolean withinDateRange(LiteratureSearchResult item, LocalDate from, LocalDate to) {
        if (item == null) {
            return false;
        }
        if (item.year() != null && item.year() >= from.getYear() && item.year() <= to.getYear()) {
            return true;
        }
        if (!hasText(item.publishedDate())) {
            return false;
        }
        try {
            LocalDate publishedDate = LocalDate.parse(item.publishedDate().trim());
            return !publishedDate.isBefore(from) && !publishedDate.isAfter(to);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String formatMatchedLiterature(List<LiteratureSearchResult> items) {
        return items.stream()
                .map(item -> {
                    String title = hasText(item.title()) ? item.title().trim() : "未命名文献";
                    String year = item.year() == null ? "年份未知" : String.valueOf(item.year());
                    String link = firstNonBlank(item.url(), item.doi(), item.externalId());
                    return !hasText(link)
                            ? "- " + title + "（" + year + "）"
                            : "- [" + title + "](" + link + ")（" + year + "）";
                })
                .reduce((left, right) -> left + "\n" + right)
                .orElse("未找到匹配文献。");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}