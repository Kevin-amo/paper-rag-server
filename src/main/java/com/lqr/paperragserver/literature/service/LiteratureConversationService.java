package com.lqr.paperragserver.literature.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.ToolCallingPromptConstructionService;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 文献会话编排服务。
 */
@Service
@RequiredArgsConstructor
public class LiteratureConversationService {

    private static final String CONVERSATION_TYPE_LITERATURE = "LITERATURE";
    private static final String LITERATURE_RESULT_TYPE = "LITERATURE_SEARCH_RESULT";
    private static final int DEFAULT_HISTORY_LIMIT = ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT;
    private static final TypeReference<ResolvedSearchPlan> PLAN_TYPE = new TypeReference<>() {
    };

    private final ConversationService conversationService;
    private final LiteratureSearchToolCallingService literatureSearchToolCallingService;
    private final ToolCallingPromptConstructionService promptConstructionService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final LiteratureSearchIntentParser intentParser;

    public LiteratureSearchResponse search(UUID ownerUserId, LiteratureSearchRequest request) {
        ConversationService.ConversationView conversation = resolveConversation(ownerUserId, request.conversationId(), request.query());
        conversationService.appendUserMessage(ownerUserId, conversation.id(), request.query());

        List<ConversationService.MessageView> history = conversationService.recentMessages(
                ownerUserId,
                conversation.id(),
                DEFAULT_HISTORY_LIMIT
        );
        ResolvedSearchPlan resolvedPlan = resolveSearchPlan(history, request);
        LiteratureSearchRequest resolvedRequest = resolvedRequest(request, conversation.id(), resolvedPlan);
        LiteratureSearchResponse searched = literatureSearchToolCallingService.search(resolvedRequest);
        String summary = buildSummary(resolvedRequest, searched.items());
        Map<String, Object> metadata = buildMetadata(resolvedRequest, searched.items());
        conversationService.appendAssistantMessage(ownerUserId, conversation.id(), summary, List.of(), metadata);
        return new LiteratureSearchResponse(conversation.id(), summary, searched.items());
    }

    private ConversationService.ConversationView resolveConversation(UUID ownerUserId, UUID conversationId, String query) {
        if (conversationId == null) {
            return conversationService.createConversation(ownerUserId, query, CONVERSATION_TYPE_LITERATURE);
        }
        ConversationService.ConversationView conversation = conversationService.requireConversation(ownerUserId, conversationId);
        if (CONVERSATION_TYPE_LITERATURE.equalsIgnoreCase(conversation.type())) {
            return conversation;
        }
        return conversationService.createConversation(ownerUserId, query, CONVERSATION_TYPE_LITERATURE);
    }

    private ResolvedSearchPlan resolveSearchPlan(List<ConversationService.MessageView> history, LiteratureSearchRequest request) {
        String contextualInput = buildContextualInput(history, request.query());
        ResolvedSearchPlan fallback = fallbackPlan(request);
        try {
            String content = llmService.generate(promptConstructionService.buildLiteratureSearchPlanPrompt(contextualInput));
            ResolvedSearchPlan plan = objectMapper.readValue(jsonObject(content), PLAN_TYPE);
            return mergePlan(plan, request, fallback);
        } catch (RuntimeException ex) {
            return fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private ResolvedSearchPlan mergePlan(ResolvedSearchPlan plan, LiteratureSearchRequest request, ResolvedSearchPlan fallback) {
        if (plan == null) {
            return fallback;
        }
        return new ResolvedSearchPlan(
                firstNonBlank(plan.query(), fallback.query()),
                request.limit() != null ? request.limit() : positiveOrNull(plan.limit()) != null ? plan.limit() : fallback.limit(),
                request.categories() == null || request.categories().isEmpty() ?
                        (plan.categories() == null || plan.categories().isEmpty() ? fallback.categories() : normalizeCategories(plan.categories())) :
                        normalizeCategories(request.categories()),
                firstNonBlank(request.dateFrom(), firstNonBlank(plan.dateFrom(), fallback.dateFrom())),
                firstNonBlank(request.sortBy(), firstNonBlank(fallback.sortBy(), plan.sortBy()))
        );
    }

    private ResolvedSearchPlan fallbackPlan(LiteratureSearchRequest request) {
        LiteratureSearchIntentParser.Intent intent = intentParser.parse(request.query());
        return new ResolvedSearchPlan(
                firstNonBlank(intent.query(), request.query()),
                request.limit() != null ? request.limit() : intent.limit(),
                normalizeCategories(request.categories()),
                normalizeText(request.dateFrom()),
                firstNonBlank(request.sortBy(), intent.sortBy())
        );
    }

    private LiteratureSearchRequest resolvedRequest(LiteratureSearchRequest request,
                                                    UUID conversationId,
                                                    ResolvedSearchPlan plan) {
        return new LiteratureSearchRequest(
                conversationId,
                firstNonBlank(plan.query(), request.query()),
                request.limit() != null ? request.limit() : positiveOrNull(plan.limit()),
                request.categories() == null || request.categories().isEmpty() ? plan.categories() : request.categories(),
                firstNonBlank(request.dateFrom(), plan.dateFrom()),
                firstNonBlank(request.sortBy(), plan.sortBy())
        );
    }

    private String buildContextualInput(List<ConversationService.MessageView> history, String currentQuery) {
        String historyText = history.stream()
                .filter(message -> message.content() != null && !message.content().isBlank())
                .map(message -> message.role() + "：" + message.content())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("(无历史)");
        return "历史对话：\n" + historyText + "\n\n当前用户请求：\n" + currentQuery;
    }

    private String buildSummary(LiteratureSearchRequest request, List<LiteratureSearchResult> items) {
        int count = items == null ? 0 : items.size();
        if (count == 0) {
            return "未找到与「" + request.query() + "」相关的论文";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("找到 ").append(count).append(" 篇与「").append(request.query()).append("」相关的论文");
        if (request.dateFrom() != null && !request.dateFrom().isBlank()) {
            summary.append("，起始时间：").append(request.dateFrom());
        }
        if (request.sortBy() != null && !request.sortBy().isBlank()) {
            summary.append("，排序：").append(request.sortBy());
        }
        return summary.toString();
    }

    private Map<String, Object> buildMetadata(LiteratureSearchRequest request, List<LiteratureSearchResult> items) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", request.limit());
        params.put("dateFrom", request.dateFrom());
        params.put("sortBy", request.sortBy());
        params.put("categories", request.categories() == null ? List.of() : request.categories());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", LITERATURE_RESULT_TYPE);
        metadata.put("query", request.query());
        metadata.put("params", params);
        metadata.put("items", items == null ? List.of() : items);
        return metadata;
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
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

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private record ResolvedSearchPlan(
            String query,
            Integer limit,
            List<String> categories,
            String dateFrom,
            String sortBy
    ) {
        private ResolvedSearchPlan {
            categories = categories == null ? List.of() : categories;
        }
    }
}