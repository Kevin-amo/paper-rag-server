package com.lqr.paperragserver.literature.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.ToolCallingPromptConstructionService;
import com.lqr.paperragserver.ai.tool.LiteratureSearchTool;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 面向自然语言文献搜索的工具调用编排服务。
 */
@Service
@RequiredArgsConstructor
public class LiteratureSearchToolCallingService {

    private static final Pattern SINGLE_RESULT_PATTERN = Pattern.compile(".*(一篇|一个|1篇|1个|one).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ABOUT_PATTERN = Pattern.compile("关于\\s*([^，。,.！？?]+)");

    private final LlmService llmService;
    private final ToolCallingPromptConstructionService promptConstructionService;
    private final LiteratureSearchTool literatureSearchTool;
    private final ObjectMapper objectMapper;

    public LiteratureSearchResponse search(LiteratureSearchRequest request) {
        SearchPlan plan = resolvePlan(request.query());
        return literatureSearchTool.searchLiterature(
                firstNonBlank(plan.query(), request.query()),
                request.limit() != null ? request.limit() : positiveOrNull(plan.limit()),
                firstNonBlank(request.sortBy(), plan.sortBy()),
                firstNonBlank(request.dateFrom(), plan.dateFrom()),
                request.categories() == null || request.categories().isEmpty() ? plan.categories() : request.categories()
        );
    }

    private SearchPlan resolvePlan(String userInput) {
        SearchPlan fallback = fallbackPlan(userInput);
        try {
            String content = llmService.generate(promptConstructionService.buildLiteratureSearchPlanPrompt(userInput));
            SearchPlan plan = objectMapper.readValue(jsonObject(content), SearchPlan.class);
            return merge(plan, fallback);
        } catch (RuntimeException ex) {
            return fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private SearchPlan merge(SearchPlan plan, SearchPlan fallback) {
        return new SearchPlan(
                firstNonBlank(plan.query(), fallback.query()),
                positiveOrNull(plan.limit()) != null ? plan.limit() : fallback.limit(),
                firstNonBlank(plan.sortBy(), fallback.sortBy()),
                firstNonBlank(plan.dateFrom(), fallback.dateFrom()),
                plan.categories() == null || plan.categories().isEmpty() ? fallback.categories() : plan.categories()
        );
    }

    private SearchPlan fallbackPlan(String userInput) {
        String query = fallbackQuery(userInput);
        Integer limit = SINGLE_RESULT_PATTERN.matcher(userInput == null ? "" : userInput).matches() ? 1 : null;
        return new SearchPlan(query, limit, null, null, List.of());
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
        text = text.replaceFirst("^(请|帮我|给我|麻烦你)?(搜|搜索|找|查找|推荐|检索)(一篇|一个|1篇|几篇)?", "");
        text = text.replaceFirst("^(一篇|一个|1篇)?关于", "");
        text = text.replaceFirst("(的)?(论文|文献|文章|paper|article)$", "");
        text = text.trim();
        return text.isBlank() ? userInput.trim() : text;
    }

    private String jsonObject(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private record SearchPlan(
            String query,
            Integer limit,
            String sortBy,
            String dateFrom,
            List<String> categories
    ) {
        private SearchPlan {
            if (categories == null) {
                categories = List.of();
            }
        }
    }
}