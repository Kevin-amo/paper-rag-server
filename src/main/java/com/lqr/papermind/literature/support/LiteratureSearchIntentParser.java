package com.lqr.papermind.literature.support;

import com.lqr.papermind.literature.model.LiteratureSearchContext;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文献搜索自然语言兜底解析。
 */
@Component
public class LiteratureSearchIntentParser {

    private static final Pattern DATE_SORT_PATTERN = Pattern.compile(".*(最新|最近|近年|\\blatest\\b|\\brecent\\b|\\bnewest\\b).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ABOUT_PATTERN = Pattern.compile("关于\\s*([^，。,.！？?]+)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2}|19\\d{2})(?!\\d)");
    private static final Pattern DIGIT_LIMIT_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(篇|个|条|篇论文|篇文献|papers?|articles?)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CHINESE_LIMIT_PATTERN = Pattern.compile("(一|两|二|三|四|五|六|七|八|九|十)\\s*(篇|个|条|篇论文|篇文献)");
    private static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(".*(有吗|有没有|有没|这些里面|这些文献|上面结果|上面的|里面|再找|再来|更多|最新的?|最近的?|换成|改成).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PREVIOUS_ITEMS_PATTERN = Pattern.compile(".*(这些里面|这些文献|上面结果|上面的|上述|里面).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final List<String> COMPOSITE_QUERY_BOUNDARIES = List.of(
            "并结合", "结合我的知识库", "结合本地知识库", "结合知识库",
            "根据我的知识库", "基于我的知识库", "用我的文档", "利用我的文档",
            "用我的论文", "利用我的论文", "根据我的文档", "基于我的文档",
            "总结趋势", "总结研究趋势", "归纳趋势", "分析趋势"
    );

    /**
     * 解析当前用户输入，不携带上一次文献搜索上下文。
     */
    public Intent parse(String userInput) {
        return parse(userInput, null);
    }

    /**
     * 解析当前用户输入，并结合上一次文献搜索上下文恢复追问条件。
     */
    public Intent parse(String userInput, LiteratureSearchContext lastContext) {
        String source = userInput == null ? "" : userInput.trim();
        Integer year = year(source);
        Integer limit = limit(source);
        boolean dateSort = DATE_SORT_PATTERN.matcher(source).matches() || year != null;
        boolean followUp = isFollowUp(source, lastContext);
        boolean withinPreviousItems = PREVIOUS_ITEMS_PATTERN.matcher(source).matches();
        String explicitQuery = fallbackQuery(source);
        boolean explicitNewTopic = hasExplicitNewTopic(source, explicitQuery);
        String query = resolveQuery(explicitQuery, explicitNewTopic, followUp, lastContext, source);
        Integer resolvedLimit = limit != null ? limit : lastContext == null ? 5 : lastContext.limit();
        if (resolvedLimit == null) {
            resolvedLimit = 5;
        }
        return new Intent(
                query,
                resolvedLimit,
                dateSort ? "date" : lastContext == null ? null : lastContext.sortBy(),
                year == null ? null : year + "-01-01",
                year == null ? null : year + "-12-31",
                lastContext == null ? List.of() : lastContext.categories(),
                followUp,
                withinPreviousItems
        );
    }

    /**
     * 在显式新主题、追问和纯筛选输入之间选择最终搜索关键词。
     */
    private String resolveQuery(String explicitQuery,
                                boolean explicitNewTopic,
                                boolean followUp,
                                LiteratureSearchContext lastContext,
                                String source) {
        if (explicitNewTopic && explicitQuery != null && !explicitQuery.isBlank()) {
            return explicitQuery;
        }
        if ((followUp || isOnlyFilter(source)) && lastContext != null && lastContext.query() != null && !lastContext.query().isBlank()) {
            return lastContext.query();
        }
        return explicitQuery == null || explicitQuery.isBlank() ? source : explicitQuery;
    }

    /**
     * 判断当前输入是否需要继承上一轮文献搜索上下文。
     */
    private boolean isFollowUp(String source, LiteratureSearchContext lastContext) {
        return lastContext != null && FOLLOW_UP_PATTERN.matcher(source).matches();
    }

    /**
     * 判断当前输入是否明确切换到了新的搜索主题。
     */
    private boolean hasExplicitNewTopic(String source, String explicitQuery) {
        if (explicitQuery == null || explicitQuery.isBlank() || isOnlyFilter(explicitQuery)) {
            return false;
        }
        return source.contains("关于")
                || source.matches("(?iu).*(搜|搜索|找|查找|推荐|检索|换成|改成|换为|改为).*");
    }

    /**
     * 判断文本是否只包含年份、数量、排序或追问表达等筛选条件。
     */
    private boolean isOnlyFilter(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String text = value.trim();
        String withoutYear = YEAR_PATTERN.matcher(text).replaceAll("");
        withoutYear = withoutYear.replaceAll("(?iu)(有吗|有没有|有没|这些里面|这些文献|上面结果|上面的|里面|再找|再来|更多|最新的?|最近的?|recent|latest|newest|年的?|篇|个|条|吗|的|[\\s，。,.！？?])", "");
        withoutYear = DIGIT_LIMIT_PATTERN.matcher(withoutYear).replaceAll("");
        withoutYear = CHINESE_LIMIT_PATTERN.matcher(withoutYear).replaceAll("");
        return withoutYear.isBlank();
    }

    /**
     * 从用户输入中提取四位年份。
     */
    private Integer year(String source) {
        Matcher matcher = YEAR_PATTERN.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * 从用户输入中提取期望返回的文献数量。
     */
    private Integer limit(String source) {
        Matcher digitMatcher = DIGIT_LIMIT_PATTERN.matcher(source);
        if (digitMatcher.find()) {
            return Math.max(1, Integer.parseInt(digitMatcher.group(1)));
        }
        Matcher chineseMatcher = CHINESE_LIMIT_PATTERN.matcher(source);
        if (chineseMatcher.find()) {
            return chineseNumber(chineseMatcher.group(1));
        }
        if (source.matches("(?iu).*(\\bone\\b|一篇|一个|1\\s*篇|1\\s*个).*")) {
            return 1;
        }
        return null;
    }

    /**
     * 将一到十的中文数量词转换为整数。
     */
    private Integer chineseNumber(String value) {
        return switch (value) {
            case "一" -> 1;
            case "两", "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> null;
        };
    }

    /**
     * 从自然语言请求中抽取可直接提交给文献服务的关键词。
     */
    private String fallbackQuery(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return userInput;
        }
        String text = truncateCompositeTask(userInput.trim());
        var aboutMatcher = ABOUT_PATTERN.matcher(text);
        if (aboutMatcher.find()) {
            text = aboutMatcher.group(1).trim();
        }
        text = text.replaceFirst("(?iu)^(请|帮我|给我|麻烦你)?\\s*(搜集|搜寻|搜索|搜|找|查找|推荐|检索|再找|再来)\\s*(一篇|一个|1\\s*篇|1\\s*个|几篇|一些|one|\\d+\\s*篇|\\d+\\s*个)?\\s*", "");
        text = text.replaceFirst("(?iu)^(换成|改成|换为|改为)\\s*", "");
        text = text.replaceFirst("(?iu)^(一篇|一个|1\\s*篇|1\\s*个|one)?\\s*关于\\s*", "");
        text = text.replaceAll("(?iu)(，|,|。|\\.|！|!|？|\\?)?\\s*(要|需要|按|排序|优先)?\\s*(最新的?|最近的?|近年的?|latest|recent|newest)\\s*(的)?\\s*", " ");
        text = DIGIT_LIMIT_PATTERN.matcher(text).replaceAll(" ");
        text = CHINESE_LIMIT_PATTERN.matcher(text).replaceAll(" ");
        text = text.replaceFirst("(?iu)\\s*(的)?\\s*(论文|文献|文章|papers?|articles?|literature)$", "");
        text = text.trim();
        return text.isBlank() || isOnlyFilter(text) ? null : text;
    }

    /**
     * 截断复合任务中的非搜索部分，只保留前置文献检索意图。
     */
    private String truncateCompositeTask(String text) {
        String candidate = text == null ? "" : text.trim();
        int boundaryIndex = -1;
        for (String boundary : COMPOSITE_QUERY_BOUNDARIES) {
            int index = candidate.indexOf(boundary);
            if (index >= 0 && (boundaryIndex < 0 || index < boundaryIndex)) {
                boundaryIndex = index;
            }
        }
        if (boundaryIndex < 0) {
            return candidate;
        }
        return candidate.substring(0, boundaryIndex).replaceFirst("[，。,.;；、\\s]+$", "").trim();
    }

    /**
     * 文献搜索意图解析结果。
     *
     * @param query 搜索关键词
     * @param limit 期望返回数量
     * @param sortBy 排序方式，例如 relevance 或 date
     * @param dateFrom 起始日期，格式为 YYYY-MM-DD
     * @param dateTo 截止日期，格式为 YYYY-MM-DD
     * @param categories 分类筛选条件列表
     * @param followUp 是否为追问，需要继承上一轮搜索上下文
     * @param withinPreviousItems 是否限定在上一轮搜索结果中筛选
     */
    public record Intent(
            String query,
            Integer limit,
            String sortBy,
            String dateFrom,
            String dateTo,
            List<String> categories,
            boolean followUp,
            boolean withinPreviousItems
    ) {
    }
}
