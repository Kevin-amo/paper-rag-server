package com.lqr.paperragserver.literature.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchContext;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 从会话消息元数据中恢复最近一次外部文献搜索状态。
 */
@Component
@RequiredArgsConstructor
public class LiteratureSearchContextResolver {

    private static final String ASSISTANT_ROLE = "ASSISTANT";
    private static final String LITERATURE_RESULT_TYPE = "LITERATURE_SEARCH_RESULT";

    private final ObjectMapper objectMapper;

    /**
     * 从会话历史中倒序查找最近一次可恢复的文献搜索上下文。
     */
    public Optional<LiteratureSearchContext> resolve(List<ConversationService.MessageView> history) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationService.MessageView message = history.get(index);
            Optional<LiteratureSearchContext> context = resolveMessage(message);
            if (context.isPresent()) {
                return context;
            }
        }
        return Optional.empty();
    }

    /**
     * 从单条助手消息的 metadata 中恢复文献搜索上下文。
     */
    private Optional<LiteratureSearchContext> resolveMessage(ConversationService.MessageView message) {
        if (message == null || !ASSISTANT_ROLE.equalsIgnoreCase(nullToEmpty(message.role()))) {
            return Optional.empty();
        }
        Map<String, Object> metadata = message.metadata();
        if (metadata == null || metadata.isEmpty()) {
            return Optional.empty();
        }
        Object literatureValue = metadata.get("literature");
        if (!(literatureValue instanceof Map<?, ?> literature)) {
            return Optional.empty();
        }
        if (!LITERATURE_RESULT_TYPE.equals(stringValue(literature.get("type")))) {
            return Optional.empty();
        }
        String query = stringValue(literature.get("query"));
        if (query.isBlank()) {
            return Optional.empty();
        }
        Map<?, ?> params = literature.get("params") instanceof Map<?, ?> value ? value : Map.of();
        return Optional.of(new LiteratureSearchContext(
                query,
                intValue(params.get("limit")),
                blankToNull(stringValue(params.get("sortBy"))),
                blankToNull(stringValue(params.get("dateFrom"))),
                blankToNull(stringValue(params.get("dateTo"))),
                stringList(params.get("categories")),
                resultList(literature.get("items")),
                message.conversationId(),
                message.id(),
                message.createdAt()
        ));
    }

    /**
     * 将 metadata 中的结果列表恢复为统一搜索结果列表。
     */
    private List<LiteratureSearchResult> resultList(Object value) {
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::toResult)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * 将单个 metadata 结果项转换为统一搜索结果。
     */
    private Optional<LiteratureSearchResult> toResult(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof LiteratureSearchResult result) {
            return Optional.of(result);
        }
        return Optional.ofNullable(objectMapper.convertValue(value, LiteratureSearchResult.class));
    }

    /**
     * 将 metadata 中的列表值转换为去重后的字符串列表。
     */
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
     * 将 metadata 值转换为整数，无法转换时返回空值。
     */
    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将 metadata 值转换为去除首尾空白的字符串。
     */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 将可能为空的角色值转换为大写字符串。
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 将空白字符串转换为空值，保留有效文本。
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}