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
                .map(item -> "[外部文献] " + nullToEmpty(item.title())
                        + "\n作者：" + String.join(", ", item.authors())
                        + "\n年份：" + nullToEmpty(item.year())
                        + "\n链接：" + nullToEmpty(firstNonBlank(item.pdfUrl(), item.url()))
                        + "\n摘要：" + cut(item.abstractText(), 700))
                .reduce((left, right) -> left + "\n\n" + right)
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

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String cut(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}