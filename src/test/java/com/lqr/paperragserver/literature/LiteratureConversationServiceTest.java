package com.lqr.paperragserver.literature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.ai.service.ToolCallingPromptConstructionService;
import com.lqr.paperragserver.conversation.service.ConversationService;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureConversationService;
import com.lqr.paperragserver.literature.service.LiteratureSearchToolCallingService;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiteratureConversationServiceTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final LiteratureSearchToolCallingService literatureSearchToolCallingService = mock(LiteratureSearchToolCallingService.class);
    private final ToolCallingPromptConstructionService promptConstructionService = mock(ToolCallingPromptConstructionService.class);
    private final LlmService llmService = mock(LlmService.class);
    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final PromptConstructionService.Prompt planPrompt = new PromptConstructionService.Prompt("sys", "user");
    private LiteratureConversationService service;

    @BeforeEach
    void setUp() {
        service = new LiteratureConversationService(
                conversationService,
                literatureSearchToolCallingService,
                promptConstructionService,
                llmService,
                new ObjectMapper(),
                new LiteratureSearchIntentParser()
        );
        when(promptConstructionService.buildLiteratureSearchPlanPrompt(anyString())).thenReturn(planPrompt);
    }

    @Test
    void searchShouldCreateLiteratureConversationAndPersistMessagesWithMetadata() {
        LiteratureSearchRequest request = new LiteratureSearchRequest("Graph RAG", null, null, null, null);
        LiteratureSearchResult result = result("Graph RAG");
        LiteratureSearchRequest resolvedRequest = new LiteratureSearchRequest(conversationId, "Graph RAG", 2, List.of(), null, "relevance");
        when(conversationService.createConversation(ownerUserId, "Graph RAG", "LITERATURE"))
                .thenReturn(conversation("Graph RAG", "LITERATURE"));
        when(conversationService.recentMessages(ownerUserId, conversationId, ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT))
                .thenReturn(List.of());
        when(llmService.generate(planPrompt))
                .thenReturn("{\"query\":\"Graph RAG\",\"limit\":2,\"categories\":[],\"dateFrom\":null,\"sortBy\":\"relevance\"}");
        when(literatureSearchToolCallingService.search(resolvedRequest))
                .thenReturn(new LiteratureSearchResponse(List.of(result)));

        LiteratureSearchResponse response = service.search(ownerUserId, request);

        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.summary()).contains("找到 1 篇").contains("Graph RAG");
        assertThat(response.items()).containsExactly(result);
        verify(conversationService).appendUserMessage(ownerUserId, conversationId, "Graph RAG");

        ArgumentCaptor<Object> metadataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(conversationService).appendAssistantMessage(
                eq(ownerUserId),
                eq(conversationId),
                eq(response.summary()),
                eq(List.of()),
                metadataCaptor.capture()
        );
        assertThat(metadataCaptor.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) metadataCaptor.getValue();
        assertThat(metadata).containsEntry("type", "LITERATURE_SEARCH_RESULT");
        assertThat(metadata).containsEntry("query", "Graph RAG");
        assertThat(metadata.get("items")).isEqualTo(List.of(result));
        assertThat(metadata.get("params")).isInstanceOf(Map.class);
    }

    @Test
    void searchShouldReuseExistingLiteratureConversation() {
        LiteratureSearchRequest request = new LiteratureSearchRequest(conversationId, "RAG evaluation", 5, List.of("cs.AI"), "2024-01-01", "date");
        LiteratureSearchResponse searched = new LiteratureSearchResponse(List.of(result("RAG evaluation")));
        when(conversationService.requireConversation(ownerUserId, conversationId))
                .thenReturn(conversation("RAG evaluation", "LITERATURE"));
        when(conversationService.recentMessages(ownerUserId, conversationId, ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT))
                .thenReturn(List.of());
        when(llmService.generate(planPrompt)).thenReturn("{}");
        when(literatureSearchToolCallingService.search(request)).thenReturn(searched);

        LiteratureSearchResponse response = service.search(ownerUserId, request);

        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.items()).isEqualTo(searched.items());
        verify(conversationService).requireConversation(ownerUserId, conversationId);
        verify(conversationService, never()).createConversation(ownerUserId, request.query(), "LITERATURE");
        verify(literatureSearchToolCallingService).search(request);
    }

    @Test
    void searchShouldResolveFollowUpQueryFromConversationHistoryBeforeLiteratureSearch() {
        LiteratureSearchRequest request = new LiteratureSearchRequest(conversationId, "只看近两年的", null, null, null, null);
        when(conversationService.requireConversation(ownerUserId, conversationId))
                .thenReturn(conversation("RAG papers", "LITERATURE"));
        when(conversationService.recentMessages(ownerUserId, conversationId, ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT))
                .thenReturn(List.of(
                        new ConversationService.MessageView(UUID.randomUUID(), conversationId, "USER", 1, "找 Graph RAG 综述", List.of(), null, null),
                        new ConversationService.MessageView(UUID.randomUUID(), conversationId, "ASSISTANT", 2, "找到 3 篇相关论文", List.of(), Map.of("type", "LITERATURE_SEARCH_RESULT"), null)
                ));
        when(llmService.generate(planPrompt))
                .thenReturn("{\"query\":\"Graph RAG review recent\",\"limit\":3,\"categories\":[],\"dateFrom\":\"2024-01-01\",\"sortBy\":\"date\"}");
        LiteratureSearchRequest resolvedRequest = new LiteratureSearchRequest(conversationId, "Graph RAG review recent", 3, List.of(), "2024-01-01", "date");
        when(literatureSearchToolCallingService.search(resolvedRequest))
                .thenReturn(new LiteratureSearchResponse(List.of(result("Recent Graph RAG"))));

        LiteratureSearchResponse response = service.search(ownerUserId, request);

        assertThat(response.items()).hasSize(1);
        ArgumentCaptor<String> contextualInputCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptConstructionService).buildLiteratureSearchPlanPrompt(contextualInputCaptor.capture());
        assertThat(contextualInputCaptor.getValue()).contains("找 Graph RAG 综述").contains("只看近两年的");
        verify(literatureSearchToolCallingService).search(resolvedRequest);
    }

    @Test
    void searchShouldFallbackToLocalDateIntentWhenPlanParsingFails() {
        LiteratureSearchRequest request = new LiteratureSearchRequest(conversationId, "搜集一篇关于RAG的文献，要最新的", null, null, null, null);
        LiteratureSearchRequest resolvedRequest = new LiteratureSearchRequest(conversationId, "RAG", 1, List.of(), null, "date");
        LiteratureSearchResponse searched = new LiteratureSearchResponse(List.of(result("Recent RAG")));
        when(conversationService.requireConversation(ownerUserId, conversationId))
                .thenReturn(conversation("RAG papers", "LITERATURE"));
        when(conversationService.recentMessages(ownerUserId, conversationId, ConversationService.DEFAULT_HISTORY_MESSAGE_LIMIT))
                .thenReturn(List.of());
        when(llmService.generate(planPrompt)).thenReturn("not json");
        when(literatureSearchToolCallingService.search(resolvedRequest)).thenReturn(searched);

        LiteratureSearchResponse response = service.search(ownerUserId, request);

        assertThat(response.items()).containsExactlyElementsOf(searched.items());
        verify(literatureSearchToolCallingService).search(resolvedRequest);
    }

    private ConversationService.ConversationView conversation(String title, String type) {
        return new ConversationService.ConversationView(conversationId, ownerUserId, title, type, null, null);
    }

    private LiteratureSearchResult result(String title) {
        return new LiteratureSearchResult(
                title,
                List.of("Alice"),
                "Abstract",
                2024,
                "2024-01-01",
                null,
                List.of("Artificial Intelligence"),
                "Artificial Intelligence",
                null,
                "https://example.org/paper",
                null,
                "openalex",
                "https://openalex.org/W123"
        );
    }
}