package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.agent.model.AgentToolResult;
import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiteratureSearchAgentToolTest {

    private final LiteratureSearchService literatureSearchService = mock(LiteratureSearchService.class);
    private final LiteratureSearchAgentTool tool = new LiteratureSearchAgentTool(literatureSearchService);

    @Test
    void executeShouldReturnLightweightEvidenceAndKeepFullAbstractInMetadata() {
        String fullAbstract = "This is a complete abstract that should remain available for the detail dialog.";
        LiteratureSearchResult result = result(
                "Graph RAG with OpenAlex",
                List.of("Alice", "Bob"),
                fullAbstract,
                List.of("Information Retrieval"),
                "Artificial Intelligence"
        );
        when(literatureSearchService.search(any())).thenReturn(new LiteratureSearchResponse(List.of(result)));

        AgentToolResult output = tool.execute(UUID.randomUUID(), Map.of("query", "Graph RAG", "limit", 1));

        assertThat(output.evidenceText())
                .contains("- [Graph RAG with OpenAlex](https://example.org/paper)")
                .contains("作者：Alice, Bob")
                .contains("年份：2024")
                .contains("分类：Artificial Intelligence")
                .doesNotContain("摘要：")
                .doesNotContain(fullAbstract);

        @SuppressWarnings("unchecked")
        Map<String, Object> literature = (Map<String, Object>) output.metadata().get("literature");
        @SuppressWarnings("unchecked")
        List<LiteratureSearchResult> items = (List<LiteratureSearchResult>) literature.get("items");
        assertThat(items).containsExactly(result);
        assertThat(items.get(0).abstractText()).isEqualTo(fullAbstract);
    }

    @Test
    void executeShouldUseFirstCategoryWhenPrimaryCategoryIsBlank() {
        LiteratureSearchResult result = result(
                "RAG Survey",
                List.of("Alice"),
                "Abstract should stay out of evidence.",
                List.of("", "Machine Learning"),
                " "
        );
        when(literatureSearchService.search(any())).thenReturn(new LiteratureSearchResponse(List.of(result)));

        AgentToolResult output = tool.execute(UUID.randomUUID(), Map.of("query", "RAG"));

        assertThat(output.evidenceText()).contains("分类：Machine Learning");
    }

    @Test
    void executeShouldUseUnknownCategoryWhenCategoryFieldsAreEmpty() {
        LiteratureSearchResult result = result(
                "RAG Survey",
                List.of("Alice"),
                "Abstract should stay out of evidence.",
                List.of(),
                null
        );
        when(literatureSearchService.search(any())).thenReturn(new LiteratureSearchResponse(List.of(result)));

        AgentToolResult output = tool.execute(UUID.randomUUID(), Map.of("query", "RAG"));

        assertThat(output.evidenceText()).contains("分类：分类未知");
    }

    private LiteratureSearchResult result(
            String title,
            List<String> authors,
            String abstractText,
            List<String> categories,
            String primaryCategory
    ) {
        return new LiteratureSearchResult(
                title,
                authors,
                abstractText,
                2024,
                "2024-01-01",
                null,
                categories,
                primaryCategory,
                "https://doi.org/10.1000/test",
                "https://example.org/paper",
                "https://example.org/paper.pdf",
                "openalex",
                "https://openalex.org/W123"
        );
    }
}