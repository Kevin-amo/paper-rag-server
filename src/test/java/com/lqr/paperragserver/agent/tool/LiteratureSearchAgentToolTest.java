package com.lqr.paperragserver.agent.tool;

import com.lqr.paperragserver.literature.model.LiteratureSearchResponse;
import com.lqr.paperragserver.literature.model.LiteratureSearchResult;
import com.lqr.paperragserver.literature.service.LiteratureSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiteratureSearchAgentToolTest {

    private final LiteratureSearchService literatureSearchService = mock(LiteratureSearchService.class);
    private final LiteratureSearchAgentTool tool = new LiteratureSearchAgentTool(literatureSearchService);

    /**
     * 验证文献工具返回轻量证据文本，同时在元数据中保留完整摘要。
     */
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

    /**
     * 验证主分类为空时，会使用分类列表中的第一个有效分类。
     */
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

    /**
     * 验证分类字段都为空时，证据文本使用未知分类兜底。
     */
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

    /**
     * 验证日期、排序和分类参数会传给文献服务，并保留在元数据中。
     */
    @Test
    void executeShouldPassDateToAndKeepParamsInMetadata() {
        LiteratureSearchResult result = result(
                "RAG 2026",
                List.of("Alice"),
                "Abstract",
                List.of("AI"),
                "AI"
        );
        when(literatureSearchService.search(argThat(request ->
                "RAG".equals(request.query())
                        && "2026-01-01".equals(request.dateFrom())
                        && "2026-12-31".equals(request.dateTo())
                        && request.categories().contains("Computer Science")
        ))).thenReturn(new LiteratureSearchResponse(List.of(result)));

        AgentToolResult output = tool.execute(UUID.randomUUID(), Map.of(
                "query", "RAG",
                "limit", 1,
                "sortBy", "date",
                "dateFrom", "2026-01-01",
                "dateTo", "2026-12-31",
                "categories", List.of("Computer Science")
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> literature = (Map<String, Object>) output.metadata().get("literature");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) literature.get("params");
        assertThat(literature).containsEntry("query", "RAG");
        assertThat(params).containsEntry("limit", 1);
        assertThat(params).containsEntry("sortBy", "date");
        assertThat(params).containsEntry("dateFrom", "2026-01-01");
        assertThat(params).containsEntry("dateTo", "2026-12-31");
        assertThat((List<LiteratureSearchResult>) literature.get("items")).containsExactly(result);
    }

    /**
     * 构造测试用文献搜索结果。
     *
     * @param title           文献标题
     * @param authors         作者列表
     * @param abstractText    摘要文本
     * @param categories      分类列表
     * @param primaryCategory 主分类
     * @return 文献搜索结果
     */
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