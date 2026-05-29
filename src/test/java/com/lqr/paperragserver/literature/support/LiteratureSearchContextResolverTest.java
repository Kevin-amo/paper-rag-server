package com.lqr.paperragserver.literature.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiteratureSearchContextResolverTest {

    private final LiteratureSearchContextResolver resolver = new LiteratureSearchContextResolver(new ObjectMapper());

    @Test
    void resolveShouldReadLatestAssistantLiteratureMetadata() {
        UUID olderMessageId = UUID.randomUUID();
        UUID latestMessageId = UUID.randomUUID();
        List<ConversationService.MessageView> history = List.of(
                message(olderMessageId, "ASSISTANT", literature("MCP", 2, "2025-01-01", null, List.of(item("MCP Paper", 2025)))),
                message(UUID.randomUUID(), "USER", Map.of("literature", Map.of("type", "LITERATURE_SEARCH_RESULT", "query", "ignored"))),
                message(latestMessageId, "ASSISTANT", literature("RAG", 3, "2026-01-01", "2026-12-31", List.of(item("RAG 2026", 2026))))
        );

        var context = resolver.resolve(history);

        assertThat(context).isPresent();
        assertThat(context.get().query()).isEqualTo("RAG");
        assertThat(context.get().limit()).isEqualTo(3);
        assertThat(context.get().dateFrom()).isEqualTo("2026-01-01");
        assertThat(context.get().dateTo()).isEqualTo("2026-12-31");
        assertThat(context.get().sourceMessageId()).isEqualTo(latestMessageId);
        assertThat(context.get().items()).hasSize(1);
        assertThat(context.get().items().get(0).title()).isEqualTo("RAG 2026");
    }

    @Test
    void resolveShouldIgnoreNonLiteratureMetadata() {
        List<ConversationService.MessageView> history = List.of(
                message(UUID.randomUUID(), "ASSISTANT", Map.of("literature", Map.of("type", "OTHER", "query", "RAG"))),
                message(UUID.randomUUID(), "USER", literature("RAG", 1, null, null, List.of()))
        );

        assertThat(resolver.resolve(history)).isEmpty();
    }

    private Map<String, Object> literature(String query,
                                           int limit,
                                           String dateFrom,
                                           String dateTo,
                                           List<Map<String, Object>> items) {
        return Map.of(
                "literature", Map.of(
                        "type", "LITERATURE_SEARCH_RESULT",
                        "query", query,
                        "params", Map.of(
                                "limit", limit,
                                "sortBy", "date",
                                "dateFrom", dateFrom == null ? "" : dateFrom,
                                "dateTo", dateTo == null ? "" : dateTo,
                                "categories", List.of("Computer Science")
                        ),
                        "items", items
                )
        );
    }

    private Map<String, Object> item(String title, int year) {
        LiteratureSearchResult result = new LiteratureSearchResult(
                title,
                List.of("Alice"),
                "Abstract",
                year,
                year + "-01-01",
                null,
                List.of("AI"),
                "AI",
                "https://doi.org/test",
                "https://example.org/" + title.replace(' ', '-'),
                null,
                "openalex",
                "W" + year
        );
        return new ObjectMapper().convertValue(result, Map.class);
    }

    private ConversationService.MessageView message(UUID id, String role, Map<String, Object> metadata) {
        return new ConversationService.MessageView(
                id,
                UUID.randomUUID(),
                role,
                1,
                "content",
                List.of(),
                metadata,
                OffsetDateTime.now()
        );
    }
}