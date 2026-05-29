package com.lqr.paperragserver.literature.support;

import com.lqr.paperragserver.literature.model.LiteratureSearchContext;

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

    public Intent parse(String userInput) {
        return parse(userInput, null);
    }

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

    private boolean isFollowUp(String source, LiteratureSearchContext lastContext) {
        return lastContext != null && FOLLOW_UP_PATTERN.matcher(source).matches();
    }

    private boolean hasExplicitNewTopic(String source, String explicitQuery) {
        if (explicitQuery == null || explicitQuery.isBlank() || isOnlyFilter(explicitQuery)) {
            return false;
        }
        return source.contains("关于")
                || source.matches("(?iu).*(搜|搜索|找|查找|推荐|检索|换成|改成|换为|改为).*");
    }

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

    private Integer year(String source) {
        Matcher matcher = YEAR_PATTERN.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

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

    private String fallbackQuery(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return userInput;
        }
        String text = userInput.trim();
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
