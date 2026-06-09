package com.lqr.papermind.agent.paper;

import com.lqr.papermind.agent.core.AgentDecision;
import com.lqr.papermind.literature.model.LiteratureSearchContext;
import com.lqr.papermind.literature.model.LiteratureSearchResult;
import com.lqr.papermind.literature.support.LiteratureSearchIntentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LiteratureContextPolicy {

    private final LiteratureSearchIntentParser intentParser;

    /**
     * 根据当前问题和上一轮文献搜索上下文补全文献搜索参数。
     *
     * @param input    待补全的工具输入参数
     * @param question 用户当前问题
     * @param context  最近一次文献搜索上下文
     */
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

    /**
     * 判断当前问题是否属于基于上一轮文献搜索状态的追问。
     *
     * @param question 用户当前问题
     * @param context  最近一次文献搜索上下文
     * @return 是否为文献搜索追问
     */
    public boolean isFollowUp(String question, LiteratureSearchContext context) {
        if (context == null || question == null) {
            return false;
        }
        return intentParser.parse(question, context).followUp();
    }

    /**
     * 在上一轮文献结果已经足够回答时直接生成结束决策，避免重复搜索。
     *
     * @param question     用户当前问题
     * @param context      最近一次文献搜索上下文
     * @param observations 当前执行轮次已有观察结果
     * @return 可直接结束的决策；不满足条件时返回 null
     */
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

    /**
     * 按日期范围筛选上一轮文献结果。
     *
     * @param items    上一轮文献结果
     * @param dateFrom 起始日期
     * @param dateTo   截止日期
     * @return 符合日期范围的文献结果
     */
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

    /**
     * 判断单条文献是否落在目标日期范围内。
     *
     * @param item 文献结果
     * @param from 起始日期
     * @param to   截止日期
     * @return 是否匹配日期范围
     */
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

    /**
     * 将匹配的文献结果格式化为最终回答可展示的列表文本。
     *
     * @param items 文献结果列表
     * @return Markdown 风格的文献列表
     */
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

    /**
     * 返回候选文本中的第一个非空白值。
     *
     * @param values 候选文本列表
     * @return 第一个有效文本；不存在时返回 null
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 判断文本是否包含非空白内容。
     *
     * @param value 待判断文本
     * @return 是否存在有效文本
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}