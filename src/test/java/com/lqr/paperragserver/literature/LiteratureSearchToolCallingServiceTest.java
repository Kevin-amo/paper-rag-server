package com.lqr.paperragserver.literature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.paperragserver.ai.service.LlmService;
import com.lqr.paperragserver.ai.service.PromptConstructionService;
import com.lqr.paperragserver.ai.service.ToolCallingPromptConstructionService;
import com.lqr.paperragserver.ai.tool.LiteratureSearchTool;
import com.lqr.paperragserver.literature.model.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureSearchToolCallingService;
import com.lqr.paperragserver.literature.support.LiteratureSearchIntentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiteratureSearchToolCallingServiceTest {

    private final LlmService llmService = mock(LlmService.class);
    private final ToolCallingPromptConstructionService promptConstructionService = mock(ToolCallingPromptConstructionService.class);
    private final LiteratureSearchTool literatureSearchTool = mock(LiteratureSearchTool.class);
    private LiteratureSearchToolCallingService service;

    @BeforeEach
    void setUp() {
        service = new LiteratureSearchToolCallingService(
                llmService,
                promptConstructionService,
                literatureSearchTool,
                new ObjectMapper(),
                new LiteratureSearchIntentParser()
        );
    }

    @Test
    void searchShouldUseLlmPlanBeforeCallingLiteratureTool() {
        LiteratureSearchRequest request = new LiteratureSearchRequest("给我搜一篇关于RAG的文章", null, null, null, null);
        PromptConstructionService.Prompt planPrompt = new PromptConstructionService.Prompt("plan-sys", "plan-user");
        LiteratureSearchResponse expected = new LiteratureSearchResponse(List.of(result("Graph RAG")));
        when(promptConstructionService.buildLiteratureSearchPlanPrompt(request.query())).thenReturn(planPrompt);
        when(llmService.generate(planPrompt)).thenReturn("{\"query\":\"RAG\",\"limit\":1,\"sortBy\":null,\"dateFrom\":null,\"categories\":[]}");
        when(literatureSearchTool.searchLiterature("RAG", 1, null, null, List.of())).thenReturn(expected);

        LiteratureSearchResponse response = service.search(request);

        assertThat(response).isEqualTo(expected);
        verify(literatureSearchTool).searchLiterature("RAG", 1, null, null, List.of());
    }

    @Test
    void searchShouldFallbackToLocalExtractionWhenLlmOutputInvalid() {
        LiteratureSearchRequest request = new LiteratureSearchRequest("帮我找一个关于RAG的文献", null, null, null, null);
        PromptConstructionService.Prompt planPrompt = new PromptConstructionService.Prompt("plan-sys", "plan-user");
        LiteratureSearchResponse expected = new LiteratureSearchResponse(List.of(result("RAG")));
        when(promptConstructionService.buildLiteratureSearchPlanPrompt(request.query())).thenReturn(planPrompt);
        when(llmService.generate(planPrompt)).thenReturn("not json");
        when(literatureSearchTool.searchLiterature("RAG", 1, null, null, List.of())).thenReturn(expected);

        LiteratureSearchResponse response = service.search(request);

        assertThat(response).isEqualTo(expected);
        verify(literatureSearchTool).searchLiterature("RAG", 1, null, null, List.of());
    }

    @Test
    void searchShouldKeepStructuredRequestFieldsOverLlmPlan() {
        LiteratureSearchRequest request = new LiteratureSearchRequest("给我搜一篇关于RAG的文章", 5, List.of("cs.AI"), "2024-01-01", "date");
        PromptConstructionService.Prompt toolPrompt = new PromptConstructionService.Prompt("tool-sys", "tool-user");
        PromptConstructionService.Prompt planPrompt = new PromptConstructionService.Prompt("plan-sys", "plan-user");
        LiteratureSearchResponse expected = new LiteratureSearchResponse(List.of(result("RAG")));
        when(promptConstructionService.buildLiteratureSearchToolCallPrompt(request.query())).thenReturn(toolPrompt);
        when(llmService.generateWithTools(toolPrompt)).thenReturn("not json");
        when(promptConstructionService.buildLiteratureSearchPlanPrompt(request.query())).thenReturn(planPrompt);
        when(llmService.generate(planPrompt)).thenReturn("{\"query\":\"RAG\",\"limit\":1,\"sortBy\":null,\"dateFrom\":null,\"categories\":[]}");
        when(literatureSearchTool.searchLiterature("RAG", 5, "date", "2024-01-01", List.of("cs.AI"))).thenReturn(expected);

        LiteratureSearchResponse response = service.search(request);

        assertThat(response).isEqualTo(expected);
        verify(literatureSearchTool).searchLiterature("RAG", 5, "date", "2024-01-01", List.of("cs.AI"));
    }

    @Test
    void searchShouldFallbackToDateSortAndSingleLimitForLatestChineseRequest() {
        LiteratureSearchRequest request = new LiteratureSearchRequest("搜集一篇关于RAG的文献，要最新的", null, null, null, null);
        PromptConstructionService.Prompt planPrompt = new PromptConstructionService.Prompt("plan-sys", "plan-user");
        LiteratureSearchResponse expected = new LiteratureSearchResponse(List.of(result("RAG")));
        when(promptConstructionService.buildLiteratureSearchPlanPrompt(request.query())).thenReturn(planPrompt);
        when(llmService.generate(planPrompt)).thenReturn("not json");
        when(literatureSearchTool.searchLiterature("RAG", 1, "date", null, List.of())).thenReturn(expected);

        LiteratureSearchResponse response = service.search(request);

        assertThat(response).isEqualTo(expected);
        verify(literatureSearchTool).searchLiterature("RAG", 1, "date", null, List.of());
    }

    @Test
    void searchShouldFallbackToDateSortForEnglishLatestTerms() {
        PromptConstructionService.Prompt planPrompt = new PromptConstructionService.Prompt("plan-sys", "plan-user");
        when(promptConstructionService.buildLiteratureSearchPlanPrompt(org.mockito.ArgumentMatchers.anyString())).thenReturn(planPrompt);
        when(llmService.generate(planPrompt)).thenReturn("not json");
        when(literatureSearchTool.searchLiterature(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("date"), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(List.of())))
                .thenReturn(new LiteratureSearchResponse(List.of(result("RAG"))));

        service.search(new LiteratureSearchRequest("latest RAG paper", null, null, null, null));
        service.search(new LiteratureSearchRequest("recent RAG paper", null, null, null, null));
        service.search(new LiteratureSearchRequest("newest RAG paper", null, null, null, null));

        verify(literatureSearchTool, times(3)).searchLiterature("RAG", null, "date", null, List.of());
    }

    @Test
    void searchShouldUseLocalDateIntentWhenLlmMissesLatestExpression() {
        LiteratureSearchRequest request = new LiteratureSearchRequest("搜集一篇关于RAG的文献，要最新的", null, null, null, null);
        PromptConstructionService.Prompt planPrompt = new PromptConstructionService.Prompt("plan-sys", "plan-user");
        LiteratureSearchResponse expected = new LiteratureSearchResponse(List.of(result("RAG")));
        when(promptConstructionService.buildLiteratureSearchPlanPrompt(request.query())).thenReturn(planPrompt);
        when(llmService.generate(planPrompt)).thenReturn("{\"query\":\"RAG\",\"limit\":1,\"sortBy\":\"relevance\",\"dateFrom\":null,\"categories\":[]}");
        when(literatureSearchTool.searchLiterature("RAG", 1, "date", null, List.of())).thenReturn(expected);

        LiteratureSearchResponse response = service.search(request);

        assertThat(response).isEqualTo(expected);
        verify(literatureSearchTool).searchLiterature("RAG", 1, "date", null, List.of());
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