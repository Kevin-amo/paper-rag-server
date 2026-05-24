package com.lqr.paperragserver.literature.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.ToolCallingPromptConstructionService;
import com.lqr.paperragserver.ai.tool.LiteratureSearchTool;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 面向自然语言文献搜索的工具调用编排服务。
 */
@Service
@RequiredArgsConstructor
public class LiteratureSearchToolCallingService {

    private final LlmService llmService;
    private final ToolCallingPromptConstructionService promptConstructionService;
    private final LiteratureSearchTool literatureSearchTool;
    private final ObjectMapper objectMapper;
    private final LiteratureSearchIntentParser intentParser;

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
                firstNonBlank(fallback.sortBy(), plan.sortBy()),
                firstNonBlank(plan.dateFrom(), fallback.dateFrom()),
                plan.categories() == null || plan.categories().isEmpty() ? fallback.categories() : plan.categories()
        );
    }

    private SearchPlan fallbackPlan(String userInput) {
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(userInput);
        return new SearchPlan(intent.query(), intent.limit(), intent.sortBy(), null, List.of());
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