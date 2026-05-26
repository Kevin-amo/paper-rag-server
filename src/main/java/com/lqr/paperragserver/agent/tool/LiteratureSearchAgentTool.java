package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部文献搜索工具，负责把智能体动作参数转换为文献检索请求，并返回可展示的搜索证据与元数据。
 */
@Component
@RequiredArgsConstructor
public class LiteratureSearchAgentTool implements AgentTool {

    private final LiteratureSearchService literatureSearchService;

    @Override
    public String name() {
        return "literature_search";
    }

    @Override
    public String description() {
        return "搜索外部学术论文和文献，适合最新研究、推荐论文、扩展阅读等请求。";
    }

    @Override
    public AgentToolResult execute(UUID ownerUserId, Map<String, Object> input) {
        String query = stringValue(input.get("query"));
        Integer limit = intValue(input.get("limit"), 5);
        String sortBy = stringValue(input.get("sortBy"));
        String dateFrom = stringValue(input.get("dateFrom"));
        if (query.isBlank()) {
            return new AgentToolResult("文献搜索跳过：query 为空。", "", List.of(), literatureMetadata(query, limit, sortBy, dateFrom, List.of()));
        }
        LiteratureSearchResponse response = literatureSearchService.search(new LiteratureSearchRequest(
                query,
                limit,
                List.of(),
                dateFrom.isBlank() ? null : dateFrom,
                sortBy.isBlank() ? null : sortBy
        ));
        List<LiteratureSearchResult> items = response.items();
        String evidence = items.stream()
                .map(this::formatEvidenceItem)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("未找到外部文献结果。");
        return new AgentToolResult(
                "外部文献搜索完成，找到 " + items.size() + " 篇论文。",
                evidence,
                List.of(),
                literatureMetadata(query, limit, sortBy, dateFrom, items)
        );
    }

    private Map<String, Object> literatureMetadata(String query, Integer limit, String sortBy, String dateFrom, List<LiteratureSearchResult> items) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        params.put("dateFrom", dateFrom == null || dateFrom.isBlank() ? null : dateFrom);
        params.put("sortBy", sortBy == null || sortBy.isBlank() ? null : sortBy);
        params.put("categories", List.of());
        Map<String, Object> literature = new LinkedHashMap<>();
        literature.put("type", "LITERATURE_SEARCH_RESULT");
        literature.put("query", query);
        literature.put("params", params);
        literature.put("items", items == null ? List.of() : items);
        return Map.of("literature", literature);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String formatEvidenceItem(LiteratureSearchResult item) {
        String title = nullToEmpty(item.title());
        String link = firstNonBlank(item.url(), item.doi(), item.externalId());
        String titleLine = link.isBlank() ? "- " + title : "- [" + title + "](" + link + ")";
        return titleLine
                + "\n  - 作者：" + String.join(", ", item.authors())
                + "\n  - 年份：" + nullToEmpty(item.year())
                + "\n  - 分类：" + categoryOf(item);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String categoryOf(LiteratureSearchResult item) {
        String primaryCategory = stringValue(item.primaryCategory());
        if (!primaryCategory.isBlank()) {
            return primaryCategory;
        }
        return item.categories().stream()
                .map(this::stringValue)
                .filter(category -> !category.isBlank())
                .findFirst()
                .orElse("分类未知");
    }
}