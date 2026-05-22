package com.lqr.paperragserver.web;

import com.lqr.paperragserver.ai.tool.LiteratureSearchTool;
import com.lqr.paperragserver.literature.LiteratureSearchRequest;
import com.lqr.paperragserver.literature.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.LiteratureSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiteratureSearchControllerTest {

    private final LiteratureSearchTool literatureSearchTool = mock(LiteratureSearchTool.class);
    private LiteratureSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new LiteratureSearchController(literatureSearchTool);
    }

    @Test
    void searchShouldExposePostEndpoint() throws NoSuchMethodException {
        Method method = LiteratureSearchController.class.getMethod("search", LiteratureSearchRequest.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/search");
    }

    @Test
    void searchShouldDelegateToTool() {
        LiteratureSearchRequest request = new LiteratureSearchRequest("Graph RAG", 10, List.of("cs.AI"), "2024-01-01", "relevance");
        LiteratureSearchResult result = new LiteratureSearchResult(
                "Graph RAG", List.of("Alice"), "Abstract", 2024, "2024-01-01", null,
                List.of("cs.AI"), "cs.AI", null, "https://example.org/paper",
                "https://example.org/paper.pdf", "openalex", "https://openalex.org/W123"
        );
        LiteratureSearchResponse expected = new LiteratureSearchResponse(List.of(result));
        when(literatureSearchTool.searchLiterature(
                request.query(),
                request.limit(),
                request.sortBy(),
                request.dateFrom(),
                request.categories()
        )).thenReturn(expected);

        LiteratureSearchResponse response = controller.search(request);

        assertThat(response).isEqualTo(expected);
        verify(literatureSearchTool).searchLiterature(
                request.query(),
                request.limit(),
                request.sortBy(),
                request.dateFrom(),
                request.categories()
        );
    }
}