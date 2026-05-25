package com.lqr.paperragserver.literature.support;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 文献搜索自然语言兜底解析。
 */
@Component
public class LiteratureSearchIntentParser {

    private static final Pattern SINGLE_RESULT_PATTERN = Pattern.compile(".*(一篇|一个|1\\s*篇|1\\s*个|\\bone\\b).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DATE_SORT_PATTERN = Pattern.compile(".*(最新|最近|近年|\\blatest\\b|\\brecent\\b|\\bnewest\\b).*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ABOUT_PATTERN = Pattern.compile("关于\\s*([^，。,.！？?]+)");

    public Intent parse(String userInput) {
        String source = userInput == null ? "" : userInput;
        return new Intent(
                fallbackQuery(userInput),
                SINGLE_RESULT_PATTERN.matcher(source).matches() ? 1 : null,
                DATE_SORT_PATTERN.matcher(source).matches() ? "date" : null
        );
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
        text = text.replaceFirst("(?iu)^(请|帮我|给我|麻烦你)?\\s*(搜集|搜寻|搜索|搜|找|查找|推荐|检索)\\s*(一篇|一个|1\\s*篇|1\\s*个|几篇|一些|one)?\\s*", "");
        text = text.replaceFirst("(?iu)^(一篇|一个|1\\s*篇|1\\s*个|one)?\\s*关于\\s*", "");
        text = text.replaceAll("(?iu)(，|,|。|\\.|！|!|？|\\?)?\\s*(要|需要|按|排序|优先)?\\s*(最新的?|最近的?|近年的?|latest|recent|newest)\\s*(的)?\\s*", " ");
        text = text.replaceFirst("(?iu)\\s*(的)?\\s*(论文|文献|文章|papers?|articles?|literature)$", "");
        text = text.trim();
        return text.isBlank() ? userInput.trim() : text;
    }

    public record Intent(
            String query,
            Integer limit,
            String sortBy
    ) {
    }
}