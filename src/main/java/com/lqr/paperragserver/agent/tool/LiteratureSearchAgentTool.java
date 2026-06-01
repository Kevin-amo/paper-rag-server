package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.common.logging.LogSanitizer;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部文献搜索工具，负责把智能体动作参数转换为文献检索请求，并返回可展示的搜索证据与元数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiteratureSearchAgentTool implements AgentTool {

    private final LiteratureSearchService literatureSearchService;

    /**
     * 返回外部文献搜索工具的注册名称。
     *
     * @return 工具名称
     */
    @Override
    public String name() {
        return "literature_search";
    }

    /**
     * 返回外部文献搜索工具的能力说明，供规划器选择工具时使用。
     *
     * @return 工具能力描述
     */
    @Override
    public String description() {
        return "搜索外部学术论文和文献，适合最新研究、推荐论文、扩展阅读等请求。";
    }

    /**
     * 执行外部文献搜索，并将搜索结果整理为智能体可消费的证据和元数据。
     *
     * @param ownerUserId 当前用户标识
     * @param input       规划器生成的搜索参数
     * @return 文献搜索工具结果
     */
    @Override
    public AgentToolResult execute(UUID ownerUserId, Map<String, Object> input) {
        long startNanos = System.nanoTime();
        String query = stringValue(input.get("query"));
        Integer limit = intValue(input.get("limit"), 5);
        String sortBy = stringValue(input.get("sortBy"));
        String dateFrom = stringValue(input.get("dateFrom"));
        String dateTo = stringValue(input.get("dateTo"));
        List<String> categories = stringList(input.get("categories"));
        log.info("agent.tool.literature_search.start ownerUserId={} queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} categoryCount={}",
                ownerUserId, LogSanitizer.safeExcerpt(query, 160), limit, sortBy, dateFrom, dateTo, categories.size());
        if (query.isBlank()) {
            log.warn("agent.tool.literature_search.skipped ownerUserId={} reason=EMPTY_QUERY", ownerUserId);
            return new AgentToolResult("文献搜索跳过：query 为空。", "", List.of(), literatureMetadata(query, limit, sortBy, dateFrom, dateTo, categories, List.of()));
        }
        LiteratureSearchResponse response = literatureSearchService.search(new LiteratureSearchRequest(
                query,
                limit,
                categories,
                dateFrom.isBlank() ? null : dateFrom,
                dateTo.isBlank() ? null : dateTo,
                sortBy.isBlank() ? null : sortBy
        ));
        List<LiteratureSearchResult> items = response.items();
        String evidence = items.stream()
                .map(this::formatEvidenceItem)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("未找到外部文献结果。");
        Map<String, Object> metadata = literatureMetadata(query, limit, sortBy, dateFrom, dateTo, categories, items);
        log.info("agent.tool.literature_search.done ownerUserId={} queryExcerpt={} limit={} sortBy={} dateFrom={} dateTo={} categoryCount={} resultCount={} metadataKeys={} costMs={}",
                ownerUserId, LogSanitizer.safeExcerpt(query, 160), limit, sortBy, dateFrom, dateTo, categories.size(), items.size(), metadata.keySet(), elapsedMs(startNanos));
        return new AgentToolResult(
                "外部文献搜索完成，找到 " + items.size() + " 篇论文。",
                evidence,
                List.of(),
                metadata
        );
    }

    /**
     * 组装外部文献搜索的结构化元数据，保留查询、参数和结果列表。
     *
     * @param query    搜索查询词
     * @param limit    结果数量上限
     * @param sortBy   排序方式
     * @param dateFrom 起始日期过滤条件
     * @param dateTo   截止日期过滤条件
     * @param categories 分类过滤条件
     * @param items    搜索结果列表
     * @return 可写入智能体结果的文献元数据
     */
    private Map<String, Object> literatureMetadata(String query,
                                                   Integer limit,
                                                   String sortBy,
                                                   String dateFrom,
                                                   String dateTo,
                                                   List<String> categories,
                                                   List<LiteratureSearchResult> items) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", limit);
        params.put("dateFrom", dateFrom == null || dateFrom.isBlank() ? null : dateFrom);
        params.put("dateTo", dateTo == null || dateTo.isBlank() ? null : dateTo);
        params.put("sortBy", sortBy == null || sortBy.isBlank() ? null : sortBy);
        params.put("categories", categories == null ? List.of() : categories);
        Map<String, Object> literature = new LinkedHashMap<>();
        literature.put("type", "LITERATURE_SEARCH_RESULT");
        literature.put("query", query);
        literature.put("params", params);
        literature.put("items", items == null ? List.of() : items);
        return Map.of("literature", literature);
    }

    /**
     * 将任意输入值转换为去除首尾空白的字符串。
     *
     * @param value 输入值
     * @return 字符串值
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 将输入值转换为正整数，无法转换时使用兜底值。
     *
     * @param value    输入值
     * @param fallback 兜底数量
     * @return 至少为 1 的整数
     */
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

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::stringValue)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 将空值转换为空字符串，便于安全拼接展示文本。
     *
     * @param value 输入值
     * @return 非空字符串或空字符串
     */
    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 将单条文献搜索结果格式化为最终回答可引用的证据文本。
     *
     * @param item 文献搜索结果
     * @return Markdown 风格的证据条目
     */
    private String formatEvidenceItem(LiteratureSearchResult item) {
        String title = nullToEmpty(item.title());
        String link = firstNonBlank(item.url(), item.doi(), item.externalId());
        String titleLine = link.isBlank() ? "- " + title : "- [" + title + "](" + link + ")";
        return titleLine
                + "\n  - 作者：" + String.join(", ", item.authors())
                + "\n  - 年份：" + nullToEmpty(item.year())
                + "\n  - 分类：" + categoryOf(item);
    }

    /**
     * 返回输入列表中第一个非空白字符串。
     *
     * @param values 候选字符串
     * @return 第一个有效字符串；不存在时返回空字符串
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 解析文献结果的主要分类，缺失时从分类列表中选取第一个有效值。
     *
     * @param item 文献搜索结果
     * @return 分类名称或默认未知分类
     */
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

    /**
     * 将纳秒起点换算为毫秒耗时，用于日志记录。
     *
     * @param startNanos 起始纳秒时间
     * @return 已经过的毫秒数
     */
    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}